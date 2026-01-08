package hal.th50743.service.impl;

import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.mapper.AssetMapper;
import hal.th50743.pojo.AssetCategory;
import hal.th50743.pojo.Asset;
import hal.th50743.pojo.Image;
import hal.th50743.service.AssetService;
import hal.th50743.utils.ImageUtils;
import hal.th50743.utils.ObjectHashUtils;
import io.minio.MinioOSSOperator;
import io.minio.MinioOSSResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static hal.th50743.utils.FileNameFormater.getSafeName;

/**
 * 对象存储服务实现类
 * <p>
 * 将上传与图片处理逻辑、数据库生命周期管理集中在此处，
 * 业务 Service（User/Message/Chat...）只负责业务校验与编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private static final int NORMALIZE_MAX_DIMENSION = 2048;
    private static final double NORMALIZE_QUALITY = 0.85;

    private static final int DEFAULT_THUMB_MAX_W = 400;
    private static final int DEFAULT_THUMB_MAX_H = 400;
    private static final int DEFAULT_IMAGE_URL_EXPIRY_MINUTES = 30;

    private final MinioOSSOperator minioOSSOperator;
    private final AssetMapper assetMapper;

    // ==================== 上传相关 ====================

    /**
     * 检查文件是否为 GIF 格式
     *
     * @param file 上传文件
     * @return 如果是 GIF 返回 true，否则返回 false
     */
    private static boolean isGifFile(MultipartFile file) {
        if (file == null)
            return false;
        String contentType = file.getContentType();
        if (contentType == null)
            return false;
        return "image/gif".equals(contentType.toLowerCase());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Image uploadAvatar(MultipartFile file) {
        // 头像属于公开资源：用于房间成员列表、通知等频繁展示场景
        return uploadImageInternal(file, true, DEFAULT_THUMB_MAX_W, DEFAULT_THUMB_MAX_H, AssetCategory.USER_AVATAR,
                null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Image uploadMessageImage(MultipartFile file) {
        // 消息图片默认走私有区：通过预签名 URL 控制访问
        // 上传时暂存为 GENERAL，消息发送成功后会激活为 MESSAGE_IMG
        return uploadImageInternal(file, false, DEFAULT_THUMB_MAX_W, DEFAULT_THUMB_MAX_H, AssetCategory.GENERAL, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StoredObject uploadFile(MultipartFile file, boolean isPublic) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }

        try {
            // 1) 文件名清洗：防止路径穿越/非法字符
            String safeName = getSafeName(file.getOriginalFilename());

            // 2) 处理文件：GIF 直接使用原图，其他图片进行规范化
            byte[] fileBytes;
            String contentType;
            String extension;
            String hashForDedup;

            if (isGifFile(file)) {
                // GIF 文件：直接使用原始文件，不进行规范化处理
                fileBytes = file.getBytes();
                contentType = file.getContentType();
                extension = ImageUtils.guessExtension(file.getOriginalFilename());
                // GIF 使用原始文件哈希进行去重
                hashForDedup = ObjectHashUtils.calculateSHA256(fileBytes);
            } else {
                // 非 GIF 文件：进行规范化处理
                ImageUtils.NormalizedFile normalized = ImageUtils.normalizeIfImage(file, NORMALIZE_MAX_DIMENSION,
                        NORMALIZE_QUALITY);
                fileBytes = normalized.bytes();
                contentType = normalized.contentType();
                extension = normalized.extension();
                // 非 GIF 使用规范化后内容的哈希进行去重
                hashForDedup = ObjectHashUtils.calculateSHA256(fileBytes);
            }

            // 3) 查询是否存在相同哈希的对象（status=1）
            Asset existingObject = findActiveObjectByNormalizedHash(hashForDedup);

            if (existingObject != null) {
                // 对象已存在，复用已存在的对象（不重复上传到 MinIO）
                log.debug("Object already exists in uploadFile, reusing: objectName={}, hash={}",
                        existingObject.getAssetName(), hashForDedup);

                // 重新生成 URL（避免预签名过期）
                String url = getImageUrl(existingObject.getAssetName());
                MinioOSSResult urls = minioOSSOperator.getImageUrls(
                        existingObject.getAssetName(),
                        DEFAULT_IMAGE_URL_EXPIRY_MINUTES,
                        TimeUnit.MINUTES);

                return new StoredObject(existingObject.getAssetName(), url, urls.getThumbUrl(), contentType);
            }

            // 4) 对象不存在，继续正常上传流程
            // 如果被规范化为 JPEG，强制后缀与 contentType 一致
            String finalName = safeName;
            if (extension != null && !extension.isBlank()) {
                finalName = ImageUtils.forceExtension(safeName, extension);
            }

            MinioOSSResult result = minioOSSOperator.upload(
                    fileBytes,
                    finalName,
                    contentType,
                    isPublic,
                    DEFAULT_THUMB_MAX_W,
                    DEFAULT_THUMB_MAX_H);

            // 5) MinIO 上传成功后，写入 objects 表（status=0, PENDING）
            saveFile(
                    result.getObjectName(),
                    file.getOriginalFilename(),
                    contentType,
                    (long) fileBytes.length,
                    AssetCategory.GENERAL,
                    null, // rawObjectHash，暂时为 null
                    hashForDedup);

            return new StoredObject(result.getObjectName(), result.getUrl(), result.getThumbUrl(), contentType);
        } catch (IOException e) {
            log.error("Upload file IO error. name={}", file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        } catch (Exception e) {
            log.error("Upload file unexpected error. name={}", file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
    }

    // ==================== 删除相关 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteObject(String objectNameOrUrl) {
        // 删除逻辑由 MinioOSSOperator 统一处理：会同时尝试删除对应缩略图
        minioOSSOperator.delete(objectNameOrUrl);
    }

    // ==================== URL 获取相关 ====================

    /**
     * 获取对象访问 URL（按需刷新预签名链接）
     * <p>
     * 业务目的：
     * - 前端预览原图时，只携带 objectName 向后端请求最新 URL
     * - 避免"预签名过期导致图片打不开"的问题，同时降低消息列表接口返回体积
     *
     * @param objectName MinIO 对象名（建议为原图 objectName）
     * @return 可访问 URL（public 为永久链接，private 为预签名链接）
     */
    @Override
    public String getImageUrl(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "objectName 不能为空");
        }
        // 限制前缀：只允许 public/private 目录，避免任意对象名被探测
        String lower = objectName.toLowerCase();
        if (!lower.startsWith("public/") && !lower.startsWith("private/")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法的 objectName");
        }
        // 统一走 getImageUrls：内部会处理 public/private 分流
        return minioOSSOperator.getImageUrls(objectName, DEFAULT_IMAGE_URL_EXPIRY_MINUTES, TimeUnit.MINUTES).getUrl();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Image getImageUrlWithObjectId(String objectName) {
        // 1. 查询 objects 表获取 objectId
        Asset asset = findByObjectName(objectName);

        // 2. 获取最新的 URL（刷新预签名）
        String url = getImageUrl(objectName);
        MinioOSSResult urls = minioOSSOperator.getImageUrls(
                objectName,
                DEFAULT_IMAGE_URL_EXPIRY_MINUTES,
                TimeUnit.MINUTES);

        // 3. 构建 Image 对象（包含 objectId）
        return new Image(objectName, url, urls.getThumbUrl(),
                asset != null ? asset.getId() : null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Image checkObjectExists(String rawHash) {
        // 1. 先查询原始对象哈希
        Asset existingObject = findActiveObjectByRawHash(rawHash);

        if (existingObject != null) {
            // 对象已存在，重新生成 URL（避免预签名过期）
            String url = getImageUrl(existingObject.getAssetName());
            MinioOSSResult urls = minioOSSOperator.getImageUrls(
                    existingObject.getAssetName(),
                    DEFAULT_IMAGE_URL_EXPIRY_MINUTES,
                    TimeUnit.MINUTES);

            return new Image(existingObject.getAssetName(), url, urls.getThumbUrl(), existingObject.getId());
        }

        // 2. 如果原始哈希不存在，再尝试规范化哈希（兼容性：如果前端规范化与后端一致）
        existingObject = findActiveObjectByNormalizedHash(rawHash);
        if (existingObject != null) {
            String url = getImageUrl(existingObject.getAssetName());
            MinioOSSResult urls = minioOSSOperator.getImageUrls(
                    existingObject.getAssetName(),
                    DEFAULT_IMAGE_URL_EXPIRY_MINUTES,
                    TimeUnit.MINUTES);

            return new Image(existingObject.getAssetName(), url, urls.getThumbUrl(), existingObject.getId());
        }

        // 3. 对象不存在，返回 null（前端继续上传）
        return null;
    }

    // ==================== 数据库记录管理 ====================

    /**
     * 保存文件记录（默认 status=0, PENDING）
     *
     * @param objectName           MinIO 对象名
     * @param originalName         原始文件名
     * @param contentType          文件 MIME 类型
     * @param fileSize             文件大小（字节）
     * @param category             文件分类
     * @param rawObjectHash        原始对象哈希（SHA-256 hex），可为 null
     * @param normalizedObjectHash 规范化对象哈希（SHA-256 hex），可为 null
     * @return 文件实体（包含自增 ID）
     */
    @Override
    public Asset saveFile(String objectName, String originalName, String contentType, Long fileSize,
            AssetCategory category, String rawObjectHash, String normalizedObjectHash) {
        Asset file = new Asset();
        file.setAssetName(objectName);
        file.setOriginalName(originalName);
        file.setContentType(contentType);
        file.setFileSize(fileSize);
        file.setCategory(category.getValue());
        file.setMessageId(null); // 初始状态不关联消息
        file.setStatus(0); // PENDING
        file.setRawAssetHash(rawObjectHash); // 原始对象哈希
        file.setNormalizedAssetHash(normalizedObjectHash); // 规范化对象哈希
        file.setCreateTime(LocalDateTime.now());
        file.setUpdateTime(LocalDateTime.now());

        assetMapper.insertAsset(file);
        log.debug("Saved file record: objectName={}, category={}, status=PENDING, rawHash={}, normalizedHash={}",
                objectName, category, rawObjectHash, normalizedObjectHash);
        return file;
    }

    /**
     * 批量激活文件（用于消息图片）
     *
     * @param objectNames objectName 列表
     * @param category    文件分类（通常为 MESSAGE_IMG）
     * @param messageId   关联的消息 ID
     */
    @Override
    public void activateFilesBatch(List<String> objectNames, AssetCategory category, Integer messageId) {
        if (objectNames == null || objectNames.isEmpty()) {
            return;
        }
        int updated = assetMapper.updateStatusBatch(objectNames, 1, category.getValue(), messageId);
        log.debug("Activated {} files batch: category={}, messageId={}", updated, category, messageId);
    }

    /**
     * 激活单个文件（用于头像/封面）
     *
     * @param objectName MinIO 对象名
     * @param category   文件分类
     */
    @Override
    public void activateFile(String objectName, AssetCategory category) {
        int updated = assetMapper.updateStatusAndCategory(objectName, 1, category.getValue());
        if (updated > 0) {
            log.debug("Activated file: objectName={}, category={}", objectName, category);
        } else {
            log.warn("File not found for activation: objectName={}", objectName);
        }
    }

    /**
     * 激活头像文件（便捷方法）
     *
     * @param objectName MinIO 对象名
     */
    @Override
    public void activateAvatarFile(String objectName) {
        activateFile(objectName, AssetCategory.USER_AVATAR);
    }

    /**
     * 激活群头像文件（便捷方法）
     *
     * @param objectName MinIO 对象名
     */
    @Override
    public void activateChatCoverFile(String objectName) {
        activateFile(objectName, AssetCategory.CHAT_COVER);
    }

    /**
     * 分页查询待清理文件（用于 GC）
     *
     * @param hoursOld  文件年龄（小时）
     * @param batchSize 每批查询数量
     * @param offset    偏移量
     * @return 文件列表
     */
    @Override
    public List<Asset> findPendingFilesForGC(int hoursOld, int batchSize, int offset) {
        LocalDateTime beforeTime = LocalDateTime.now().minusHours(hoursOld);
        return assetMapper.selectPendingFilesBefore(beforeTime, batchSize, offset);
    }

    /**
     * 根据原始对象哈希查询已激活的对象（用于前端轻量级比对）
     *
     * @param rawHash 原始对象哈希（SHA-256 hex）
     * @return 对象实体，不存在返回 null
     */
    @Override
    public Asset findActiveObjectByRawHash(String rawHash) {
        return assetMapper.selectByRawHashAndActive(rawHash);
    }

    /**
     * 根据规范化对象哈希查询已激活的对象（用于后端最终去重）
     *
     * @param normalizedHash 规范化对象哈希（SHA-256 hex）
     * @return 对象实体，不存在返回 null
     */
    @Override
    public Asset findActiveObjectByNormalizedHash(String normalizedHash) {
        return assetMapper.selectByNormalizedHashAndActive(normalizedHash);
    }

    /**
     * 根据 ID 查询对象实体
     *
     * @param id 对象 ID
     * @return 对象实体，不存在返回 null
     */
    @Override
    public Asset findById(Integer id) {
        return assetMapper.selectById(id);
    }

    /**
     * 根据 objectName 查询对象实体
     *
     * @param objectName MinIO 对象名
     * @return 对象实体，不存在返回 null
     */
    @Override
    public Asset findByObjectName(String objectName) {
        return assetMapper.selectByObjectName(objectName);
    }

    // ==================== 内部实现方法 ====================

    /**
     * 上传图片的内部实现
     *
     * @param file          上传文件
     * @param isPublic      是否公开访问
     * @param maxW          缩略图最大宽度
     * @param maxH          缩略图最大高度
     * @param category      文件分类
     * @param rawObjectHash 原始对象哈希（前端计算，可为 null）
     * @return Image
     */
    @Transactional(rollbackFor = Exception.class)
    private Image uploadImageInternal(MultipartFile file, boolean isPublic, int maxW, int maxH, AssetCategory category,
            String rawObjectHash) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }

        String originalFilename = file.getOriginalFilename();
        try {
            // 1) 文件名清洗
            String safeName = getSafeName(originalFilename);

            // 2) 处理文件：GIF 直接使用原图，其他图片进行规范化
            byte[] fileBytes;
            String contentType;
            String extension;
            String hashForDedup;

            if (isGifFile(file)) {
                // GIF 文件：直接使用原始文件，不进行规范化处理
                fileBytes = file.getBytes();
                contentType = file.getContentType();
                extension = ImageUtils.guessExtension(originalFilename);
                // GIF 使用原始文件哈希进行去重
                hashForDedup = ObjectHashUtils.calculateSHA256(fileBytes);
            } else {
                // 非 GIF 文件：进行规范化处理
                ImageUtils.NormalizedFile normalized = ImageUtils.normalizeIfImage(file, NORMALIZE_MAX_DIMENSION,
                        NORMALIZE_QUALITY);
                fileBytes = normalized.bytes();
                contentType = normalized.contentType();
                extension = normalized.extension();
                // 非 GIF 使用规范化后内容的哈希进行去重
                hashForDedup = ObjectHashUtils.calculateSHA256(fileBytes);
            }

            // 3) 查询是否存在相同哈希的对象（status=1）
            Asset existingObject = findActiveObjectByNormalizedHash(hashForDedup);

            if (existingObject != null) {
                // 对象已存在，复用已存在的对象（不重复上传到 MinIO）
                log.debug("Object already exists, reusing: objectName={}, hash={}",
                        existingObject.getAssetName(), hashForDedup);

                // 重新生成 URL（避免预签名过期）
                String url = getImageUrl(existingObject.getAssetName());
                MinioOSSResult urls = minioOSSOperator.getImageUrls(
                        existingObject.getAssetName(),
                        DEFAULT_IMAGE_URL_EXPIRY_MINUTES,
                        TimeUnit.MINUTES);

                return new Image(existingObject.getAssetName(), url, urls.getThumbUrl(), existingObject.getId());
            }

            // 4) 对象不存在，继续正常上传流程
            // 强制后缀与 contentType 一致（例如转为 .jpg）
            String finalName = safeName;
            if (extension != null && !extension.isBlank()) {
                finalName = ImageUtils.forceExtension(safeName, extension);
            }

            // 5) 上传到 MinIO（缩略图由 MinioOSSOperator 内部按尺寸阈值决定是否生成）
            MinioOSSResult result = minioOSSOperator.upload(
                    fileBytes,
                    finalName,
                    contentType,
                    isPublic,
                    maxW,
                    maxH);

            // 6) 如果前端未提供 rawObjectHash，设置为 null（向后兼容）
            String finalRawObjectHash = rawObjectHash;
            if (finalRawObjectHash == null || finalRawObjectHash.isBlank()) {
                finalRawObjectHash = null;
            }

            // 7) MinIO 上传成功后，写入 objects 表（status=0, PENDING）
            Asset savedFile = saveFile(
                    result.getObjectName(),
                    file.getOriginalFilename(),
                    contentType,
                    (long) fileBytes.length,
                    category,
                    finalRawObjectHash,
                    hashForDedup);

            // 8) 返回 Image 对象（包含 objectId）
            return new Image(result.getObjectName(), result.getUrl(), result.getThumbUrl(), savedFile.getId());
        } catch (IOException e) {
            log.error("Upload image IO error. name={}", originalFilename, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        } catch (Exception e) {
            log.error("Upload image unexpected error. name={}", originalFilename, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
    }
}
