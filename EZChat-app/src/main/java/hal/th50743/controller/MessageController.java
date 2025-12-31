package hal.th50743.controller;

import hal.th50743.pojo.Image;
import hal.th50743.pojo.Result;
import hal.th50743.service.MessageService;
import hal.th50743.utils.CurrentHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 消息相关的API控制器
 * <p>
 * 处理消息的获取、上传等请求。
 */
@Slf4j
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {
    
    private final MessageService messageService;

    /**
     * 根据聊天室代码获取消息列表
     * URL 示例: /message?chatCode=ABC&timeStamp=2023-01-01
     * @param chatCode 聊天室的唯一代码 (来自查询参数)
     * @param timeStamp 可选的时间戳 (来自查询参数)
     * @return 包含消息列表的统一响应结果
     */
    @GetMapping
    public Result getMessagesByChatCode(@RequestParam String chatCode,
                                        @RequestParam(required = false) String timeStamp){
        // 从线程上下文中获取当前登录用户的ID
        Integer userID = CurrentHolder.getCurrentId();
        // 调用服务层获取消息，并返回成功响应
        return Result.success(messageService.getMessagesByChatCode(userID, chatCode, timeStamp));
    }

    /**
     * 上传消息附件（如图片）
     *
     * @param file 附件文件
     * @return 上传成功后的图片信息
     */
    @PostMapping("/upload")
    public Result upload(@RequestParam("file") MultipartFile file) {
        Image image = messageService.upload(file);
        return Result.success(image);
    }
    
}
