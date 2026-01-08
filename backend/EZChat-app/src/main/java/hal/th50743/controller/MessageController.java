package hal.th50743.controller;

import hal.th50743.pojo.*;
import hal.th50743.service.MessageService;

import java.util.List;
import java.util.Map;
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
     * 根据聊天室代码获取消息列表 (分页)
     * URL 示例: /message?chatCode=ABC&cursorSeqId=100
     *
     * @param chatCode    聊天室的唯一代码
     * @param cursorSeqId 游标序列号 (查询小于此序列号的消息)，为空时不限制 (查最新)
     * @return 包含消息列表的统一响应结果
     */
    @GetMapping
    public Result<Map<String, Object>> getMessagesByChatCode(@RequestParam String chatCode,
            @RequestParam(required = false) Long cursorSeqId) {
        // 从线程上下文中获取当前登录用户的ID
        Integer userID = CurrentHolder.getCurrentId();
        // 调用服务层获取消息，并返回成功响应
        return Result.success(messageService.getMessagesByChatCode(userID, chatCode, cursorSeqId));
    }

    /**
     * 同步消息（拉取指定序列号之后的消息）
     * 用于前端检测到 seqId 不连续时或重连时补齐消息
     *
     * @param chatCode  聊天室代码
     * @param lastSeqId 本地最后一条消息的序列号
     * @return 缺失的消息列表
     */
    @GetMapping("/sync")
    public Result<List<MessageVO>> syncMessages(@RequestParam String chatCode,
            @RequestParam Long lastSeqId) {
        Integer userId = CurrentHolder.getCurrentId();
        return Result.success(messageService.syncMessages(userId, chatCode, lastSeqId));
    }

    /**
     * 上传消息附件（如图片）
     *
     * @param file 附件文件
     * @return 上传成功后的图片信息
     */
    @PostMapping("/upload")
    public Result<Image> upload(@RequestParam("file") MultipartFile file) {
        Image image = messageService.upload(file);
        return Result.success(image);
    }

}
