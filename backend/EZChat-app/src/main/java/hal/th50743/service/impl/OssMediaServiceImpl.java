package hal.th50743.service.impl;

import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.pojo.FileCategory;
import hal.th50743.pojo.FileEntity;
import hal.th50743.pojo.Image;
import hal.th50743.service.FileService;
import hal.th50743.service.OssMediaService;
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
import java.util.concurrent.TimeUnit;

import static hal.th50743.utils.FileNameFormater.getSafeName;

/**
 * 对象存储媒体服务实现类
 * <p>
 * 将上传与图片处理逻辑集中在此处，业务 Service（User/Message/Chat...）只负责业务校验与编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssMediaServiceImpl implements OssMediaService {

    private static final int NORMALIZE_MAX_DIMENSION = 2048;
    private static final double NORMALIZE_QUALITY = 0.85;

    private static final int DEFAULT_THUMB_MAX_W = 400;
    private static final int DEFAULT_THUMB_MAX_H = 400;
    private static final int DEFAULT_IMAGE_URL_EXPIRY_MINUTES = 30;

    private final MinioOSSOperator minioOSSOperator;
    private final FileService fileService;

    /**
     * 检查文件是否为 GIF 格式
     *
     * @param file 上传文件
     * @return 如果是 GIF 返回 true，否则返回 false
     */
    private static boolean isGifFile(MultipartFile file) {
        if (file == null) return false;
        String contentType = file.getContentType();
        if (contentType == null) return false;
        return "image/gif".equals(contentType.toLowerCase());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Image uploadAvatar(MultipartFile file) {
        // 头像属于公开资源：用于房间成员列表、通知等频繁展示场景
        // 注意：暂时不传递 rawObjectHash，后续可以通过请求参数或 header 传递
        Image image = uploadImageInternal(file, true, DEFAULT_THUMB_MAX_W, DEFAULT_THUMB_MAX_H, FileCategory.USER_AVATAR, null);
        return image;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Image uploadMessageImage(MultipartFile file) {
        // 消息图片默认走私有区：通过预签名 URL 控制访问
        // 上传时暂存为 GENERAL，消息发送成功后会激活为 MESSAGE_IMG
        // 注意：暂时不传递 rawObjectHash，后续可以通过请求参数或 header 传递
        Image image = uploadImageInternal(file, false, DEFAULT_THUMB_MAX_W, DEFAULT_THUMB_MAX_H, FileCategory.GENERAL, null);
        return image;
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
                ImageUtils.NormalizedFile normalized = ImageUtils.normalizeIfImage(file, NORMALIZE_MAX_DIMENSION, NORMALIZE_QUALITY);
                fileBytes = normalized.bytes();
                contentType = normalized.contentType();
                extension = normalized.extension();
                // 非 GIF 使用规范化后内容的哈希进行去重
                hashForDedup = ObjectHashUtils.calculateSHA256(fileBytes);
            }

            // 3) 查询是否存在相同哈希的对象（status=1）
            // 注意：GIF 使用原始哈希，其他图片使用规范化哈希
            FileEntity existingObject = fileService.findActiveObjectByNormalizedHash(hashForDedup);
            
            if (existingObject != null) {
                // 对象已存在，复用已存在的对象（不重复上传到 MinIO）
                log.debug("Object already exists in uploadFile, reusing: objectName={}, hash={}", 
                    existingObject.getObjectName(), hashForDedup);
                
                // 重新生成 URL（避免预签名过期）
                String url = getImageUrl(existingObject.getObjectName());
                MinioOSSResult urls = minioOSSOperator.getImageUrls(
                        existingObject.getObjectName(), 
                        DEFAULT_IMAGE_URL_EXPIRY_MINUTES,
                        TimeUnit.MINUTES
                );
                
                return new StoredObject(existingObject.getObjectName(), url, urls.getThumbUrl(), contentType);
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
                    DEFAULT_THUMB_MAX_H
            );
            
            // 5) MinIO 上传成功后，写入 objects 表（status=0, PENDING）
            // 根据业务上下文确定 category：通用上传暂定为 GENERAL，后续可通过业务逻辑激活
            // 注意：暂时传入 null 作为 rawObjectHash，后续可以通过请求参数传递
            fileService.saveFile(
                    result.getObjectName(),
                    file.getOriginalFilename(),
                    contentType,
                    (long) fileBytes.length,
                    FileCategory.GENERAL,
                    null, // rawObjectHash，暂时为 null
                    hashForDedup
            );
            
            return new StoredObject(result.getObjectName(), result.getUrl(), result.getThumbUrl(), contentType);
        } catch (IOException e) {
            log.error("Upload file IO error. name={}", file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        } catch (Exception e) {
            log.error("Upload file unexpected error. name={}", file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteObject(String objectNameOrUrl) {
        // 删除逻辑由 MinioOSSOperator 统一处理：会同时尝试删除对应缩略图
        minioOSSOperator.delete(objectNameOrUrl);
    }

    /**
     * 获取对象访问 URL（按需刷新预签名链接）
     * <p>
     * 业务目的：
     * - 前端预览原图时，只携带 objectName 向后端请求最新 URL
     * - 避免“预签名过期导致图片打不开”的问题，同时降低消息列表接口返回体积
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
    private Image uploadImageInternal(MultipartFile file, boolean isPublic, int maxW, int maxH, FileCategory category, String rawObjectHash) {
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
                ImageUtils.NormalizedFile normalized = ImageUtils.normalizeIfImage(file, NORMALIZE_MAX_DIMENSION, NORMALIZE_QUALITY);
                fileBytes = normalized.bytes();
                contentType = normalized.contentType();
                extension = normalized.extension();
                // 非 GIF 使用规范化后内容的哈希进行去重
                hashForDedup = ObjectHashUtils.calculateSHA256(fileBytes);
            }

            // 3) 查询是否存在相同哈希的对象（status=1）
            // 注意：GIF 使用原始哈希，其他图片使用规范化哈希
            FileEntity existingObject = fileService.findActiveObjectByNormalizedHash(hashForDedup);
            
            if (existingObject != null) {
                // 对象已存在，复用已存在的对象（不重复上传到 MinIO）
                log.debug("Object already exists, reusing: objectName={}, hash={}", 
                    existingObject.getObjectName(), hashForDedup);
                
                // 重新生成 URL（避免预签名过期）
                String url = getImageUrl(existingObject.getObjectName());
                MinioOSSResult urls = minioOSSOperator.getImageUrls(
                        existingObject.getObjectName(), 
                        DEFAULT_IMAGE_URL_EXPIRY_MINUTES,
                        TimeUnit.MINUTES
                );
                
                return new Image(existingObject.getObjectName(), url, urls.getThumbUrl(), existingObject.getId());
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
                    maxH
            );
            
            // 6) 如果前端未提供 rawObjectHash，从原始对象计算（需要保留原始对象字节流）
            // 注意：由于前端已经压缩/规范化，这里可能无法获取真正的原始对象
            // 如果前端未提供，可以设置为 null（向后兼容）
            String finalRawObjectHash = rawObjectHash;
            if (finalRawObjectHash == null || finalRawObjectHash.isBlank()) {
                // 可选：从原始文件计算（如果可能）
                // finalRawObjectHash = ObjectHashUtils.calculateSHA256(file.getBytes());
                finalRawObjectHash = null; // 暂时设为 null，后续可以优化
            }
            
            // 7) MinIO 上传成功后，写入 objects 表（status=0, PENDING）
            FileEntity savedFile = fileService.saveFile(
                    result.getObjectName(),
                    file.getOriginalFilename(),
                    contentType,
                    (long) fileBytes.length,
                    category,
                    finalRawObjectHash,
                    hashForDedup
            );
            
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

    /**
     * 上传图片的内部实现（重载方法，兼容旧调用）
     *
     * @param file     上传文件
     * @param isPublic 是否公开访问
     * @param maxW     缩略图最大宽度
     * @param maxH     缩略图最大高度
     * @param category 文件分类
     * @return Image
     */
    @Transactional(rollbackFor = Exception.class)
    private Image uploadImageInternal(MultipartFile file, boolean isPublic, int maxW, int maxH, FileCategory category) {
        return uploadImageInternal(file, isPublic, maxW, maxH, category, null);
    }
}


