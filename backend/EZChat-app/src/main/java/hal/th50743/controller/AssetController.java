package hal.th50743.controller;

import hal.th50743.pojo.Image;
import hal.th50743.pojo.Result;
import hal.th50743.service.AssetService;
import hal.th50743.utils.CurrentHolder;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 媒体资源控制器
 *
 * <p>提供媒体资源相关的 RESTful API 端点，包括图片 URL 刷新、去重检查等功能。
 *
 * <h3>API 列表</h3>
 * <ul>
 *   <li>GET /media/url - 获取图片访问 URL</li>
 *   <li>GET /media/check - 检查对象是否已存在</li>
 * </ul>
 *
 * <h3>认证要求</h3>
 * <p>所有接口需要 Header: token
 *
 * @see AssetService
 */
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
@Validated
public class AssetController {

    private final AssetService assetService;

    /**
     * 获取图片访问 URL
     *
     * <p>刷新 MinIO 预签名链接，用于点开大图预览时获取最新 URL。
     *
     * @param objectName MinIO 对象名
     * @return 图片信息（包含最新 URL 和 assetId）
     */
    @GetMapping("/url")
    public Result<Image> getImageUrl(@RequestParam String objectName) {
        Integer userId = CurrentHolder.getCurrentId();
        Image image = assetService.getImageUrlWithAssetId(objectName, userId);
        return Result.success(image);
    }

    /**
     * 检查对象是否已存在
     *
     * <p>前端计算原始文件哈希后调用，用于去重避免重复上传。
     * 如果对象已存在返回图片信息，否则返回 null。
     *
     * @param rawHash 原始文件哈希（SHA-256，64 位十六进制）
     * @return 图片信息（已存在）或 null（不存在）
     */
    @GetMapping("/check")
    public Result<Image> checkObjectExists(
            @RequestParam @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "Invalid hash format") String rawHash) {
        Image image = assetService.checkAssetExists(rawHash);
        return Result.success(image);
    }
}
