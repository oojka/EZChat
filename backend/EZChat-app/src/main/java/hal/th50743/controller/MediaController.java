package hal.th50743.controller;

import hal.th50743.pojo.Result;
import hal.th50743.service.OssMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 媒体相关 API 控制器
 * <p>
 * 业务目的：
 * - 为前端提供“按需获取图片 URL（刷新预签名）”的能力
 * - 避免消息列表/房间列表接口返回的预签名过期导致预览失败
 */
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final OssMediaService ossMediaService;

    /**
     * 获取图片访问 URL（按需刷新预签名链接）
     * <p>
     * 业务用途：前端在“点开大图预览”时调用，拿到最新 URL 后再去拉取原图内容。
     *
     * @param objectName MinIO 对象名（建议为原图 objectName）
     * @return 统一响应：data 为可访问 URL（String）
     */
    @GetMapping("/url")
    public Result getImageUrl(@RequestParam String objectName) {
        return Result.success(ossMediaService.getImageUrl(objectName));
    }
}


