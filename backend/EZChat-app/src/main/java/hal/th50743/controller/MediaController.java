package hal.th50743.controller;

import hal.th50743.pojo.FileEntity;
import hal.th50743.pojo.Image;
import hal.th50743.pojo.Result;
import hal.th50743.service.FileService;
import hal.th50743.service.OssMediaService;
import io.minio.MinioOSSOperator;
import io.minio.MinioOSSResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 媒体相关 API 控制器
 * <p>
 * 业务目的：
 * - 为前端提供"按需获取图片 URL（刷新预签名）"的能力
 * - 避免消息列表/房间列表接口返回的预签名过期导致预览失败
 * - 提供对象去重检查接口，避免重复上传
 */
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private static final int DEFAULT_THUMB_MAX_W = 400;
    private static final int DEFAULT_THUMB_MAX_H = 400;
    private static final int DEFAULT_IMAGE_URL_EXPIRY_MINUTES = 30;

    private final OssMediaService ossMediaService;
    private final FileService fileService;
    private final MinioOSSOperator minioOSSOperator;

    /**
     * 获取图片访问 URL（按需刷新预签名链接）
     * <p>
     * 业务用途：前端在"点开大图预览"时调用，拿到最新 URL 后再去拉取原图内容。
     * <p>
     * 优化：返回 Image 对象（包含 objectId），便于前端直接使用 objectId 进行后续操作。
     *
     * @param objectName MinIO 对象名（建议为原图 objectName）
     * @return 统一响应：data 为 Image 对象（包含最新的 URL 和 objectId）
     */
    @GetMapping("/url")
    public Result<Image> getImageUrl(@RequestParam String objectName) {
        // 1. 查询 objects 表获取 objectId
        FileEntity fileEntity = fileService.findByObjectName(objectName);
        
        // 2. 获取最新的 URL（刷新预签名）
        String url = ossMediaService.getImageUrl(objectName);
        MinioOSSResult urls = minioOSSOperator.getImageUrls(
                objectName, 
                DEFAULT_IMAGE_URL_EXPIRY_MINUTES, 
                TimeUnit.MINUTES
        );
        
        // 3. 构建 Image 对象（包含 objectId）
        Image image = new Image(objectName, url, urls.getThumbUrl(), 
                fileEntity != null ? fileEntity.getId() : null);
        
        return Result.success(image);
    }

    /**
     * 检查对象是否已存在（轻量级比对接口）
     * <p>
     * 业务目的：前端计算原始对象哈希后，先调用此接口比对，避免不必要的对象上传。
     * <p>
     * 查询逻辑：
     * 1. 先查询 raw_object_hash（前端计算的原始对象哈希）
     * 2. 如果不存在，再查询 normalized_object_hash（兼容性：如果前端规范化与后端一致）
     * 3. 只查询 status=1（已激活）的对象
     * <p>
     * 返回逻辑：
     * - 如果对象已存在，返回 Image 对象（包含最新的 URL，避免预签名过期）
     * - 如果不存在，返回 null（前端继续上传）
     *
     * @param rawHash 原始对象哈希（SHA-256 hex，64 字符）
     * @return 统一响应：如果对象已存在，data 为 Image 对象；不存在返回 null
     */
    @GetMapping("/check")
    public Result<Image> checkObjectExists(@RequestParam String rawHash) {
        // 参数校验
        if (rawHash == null || rawHash.length() != 64) {
            return Result.error("Invalid hash format");
        }

        // 1. 先查询原始对象哈希
        FileEntity existingObject = fileService.findActiveObjectByRawHash(rawHash);
        
        if (existingObject != null) {
            // 对象已存在，重新生成 URL（避免预签名过期）
            String url = ossMediaService.getImageUrl(existingObject.getObjectName());
            // 获取缩略图 URL
            MinioOSSResult urls = minioOSSOperator.getImageUrls(
                    existingObject.getObjectName(), 
                    DEFAULT_IMAGE_URL_EXPIRY_MINUTES, 
                    TimeUnit.MINUTES
            );
            
            Image image = new Image(existingObject.getObjectName(), url, urls.getThumbUrl(), existingObject.getId());
            return Result.success(image);
        }

        // 2. 如果原始哈希不存在，再尝试规范化哈希（兼容性：如果前端规范化与后端一致）
        existingObject = fileService.findActiveObjectByNormalizedHash(rawHash);
        if (existingObject != null) {
            String url = ossMediaService.getImageUrl(existingObject.getObjectName());
            MinioOSSResult urls = minioOSSOperator.getImageUrls(
                    existingObject.getObjectName(), 
                    DEFAULT_IMAGE_URL_EXPIRY_MINUTES, 
                    TimeUnit.MINUTES
            );
            
            Image image = new Image(existingObject.getObjectName(), url, urls.getThumbUrl(), existingObject.getId());
            return Result.success(image);
        }

        // 3. 对象不存在，返回 null（前端继续上传）
        return Result.success(null);
    }
}


