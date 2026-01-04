package hal.th50743.controller;

import hal.th50743.pojo.Image;
import hal.th50743.pojo.Result;
import hal.th50743.service.OssMediaService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
@Validated
public class MediaController {

    private final OssMediaService ossMediaService;

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
        Image image = ossMediaService.getImageUrlWithObjectId(objectName);
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
    public Result<Image> checkObjectExists(
            @RequestParam 
            @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "Invalid hash format") 
            String rawHash) {
        Image image = ossMediaService.checkObjectExists(rawHash);
        return Result.success(image);
    }
}


