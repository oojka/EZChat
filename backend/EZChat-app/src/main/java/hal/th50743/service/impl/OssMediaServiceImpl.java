package hal.th50743.service.impl;

import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.pojo.Image;
import hal.th50743.service.OssMediaService;
import hal.th50743.utils.ImageUtils;
import io.minio.MinioOSSOperator;
import io.minio.MinioOSSResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Image uploadAvatar(MultipartFile file) {
        // 头像属于公开资源：用于房间成员列表、通知等频繁展示场景
        return uploadImageInternal(file, true, DEFAULT_THUMB_MAX_W, DEFAULT_THUMB_MAX_H);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Image uploadMessageImage(MultipartFile file) {
        // 消息图片默认走私有区：通过预签名 URL 控制访问
        return uploadImageInternal(file, false, DEFAULT_THUMB_MAX_W, DEFAULT_THUMB_MAX_H);
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

            // 2) 对图片做规范化（非图片原样返回）
            ImageUtils.NormalizedFile normalized = ImageUtils.normalizeIfImage(file, NORMALIZE_MAX_DIMENSION, NORMALIZE_QUALITY);

            // 3) 如果被规范化为 JPEG，强制后缀与 contentType 一致
            String finalName = safeName;
            if (normalized.extension() != null && !normalized.extension().isBlank()) {
                finalName = ImageUtils.forceExtension(safeName, normalized.extension());
            }

            MinioOSSResult result = minioOSSOperator.upload(
                    normalized.bytes(),
                    finalName,
                    normalized.contentType(),
                    isPublic,
                    DEFAULT_THUMB_MAX_W,
                    DEFAULT_THUMB_MAX_H
            );
            return new StoredObject(result.getObjectName(), result.getUrl(), result.getThumbUrl(), normalized.contentType());
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
     * @param file     上传文件
     * @param isPublic 是否公开访问
     * @param maxW     缩略图最大宽度
     * @param maxH     缩略图最大高度
     * @return Image
     */
    private Image uploadImageInternal(MultipartFile file, boolean isPublic, int maxW, int maxH) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }

        String originalFilename = file.getOriginalFilename();
        try {
            // 1) 文件名清洗
            String safeName = getSafeName(originalFilename);

            // 2) 图片规范化：尽可能统一为 JPEG（兼容/隐私/方向修复）
            ImageUtils.NormalizedFile normalized = ImageUtils.normalizeIfImage(file, NORMALIZE_MAX_DIMENSION, NORMALIZE_QUALITY);

            // 3) 强制后缀与 contentType 一致（例如转为 .jpg）
            String finalName = safeName;
            if (normalized.extension() != null && !normalized.extension().isBlank()) {
                finalName = ImageUtils.forceExtension(safeName, normalized.extension());
            }

            // 4) 上传（缩略图由 MinioOSSOperator 内部按尺寸阈值决定是否生成）
            MinioOSSResult result = minioOSSOperator.upload(
                    normalized.bytes(),
                    finalName,
                    normalized.contentType(),
                    isPublic,
                    maxW,
                    maxH
            );
            return new Image(result.getObjectName(), result.getUrl(), result.getThumbUrl());
        } catch (IOException e) {
            log.error("Upload image IO error. name={}", originalFilename, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        } catch (Exception e) {
            log.error("Upload image unexpected error. name={}", originalFilename, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
    }
}


