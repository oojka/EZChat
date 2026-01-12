package hal.th50743.controller;

import hal.th50743.pojo.*;
import hal.th50743.service.MessageService;

import java.util.List;
import hal.th50743.utils.CurrentHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 消息控制器
 *
 * <p>提供消息相关的 RESTful API 端点，包括消息获取、同步、附件上传等功能。
 *
 * <h3>API 列表</h3>
 * <ul>
 *   <li>GET /message - 获取消息列表（分页）</li>
 *   <li>GET /message/sync - 同步缺失消息</li>
 *   <li>POST /message/upload - 上传消息附件</li>
 * </ul>
 *
 * <h3>认证要求</h3>
 * <p>所有接口需要 Header: token
 *
 * @see MessageService
 */
@Slf4j
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * 获取消息列表
     *
     * <p>基于游标分页获取指定聊天室的历史消息。
     *
     * @param chatCode 聊天室对外唯一标识
     * @param cursorSeqId 游标序列号（查询小于此值的消息，空则查最新）
     * @return 消息列表（包含是否还有更多）
     */
    @GetMapping
    public Result<hal.th50743.pojo.MessageListVO> getMessagesByChatCode(@RequestParam String chatCode,
            @RequestParam(required = false) Long cursorSeqId) {
        // 从线程上下文中获取当前登录用户的ID
        Integer userID = CurrentHolder.getCurrentId();
        // 调用服务层获取消息，并返回成功响应
        return Result.success(messageService.getMessagesByChatCode(userID, chatCode, cursorSeqId));
    }

    /**
     * 同步缺失消息
     *
     * <p>拉取指定序列号之后的消息，用于前端检测到 seqId 不连续或重连时补齐。
     *
     * @param chatCode 聊天室对外唯一标识
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
     * 上传消息附件
     *
     * <p>上传图片等消息附件，返回可用于发送消息的图片信息。
     *
     * @param file 附件文件
     * @return 上传成功的图片信息
     */
    @PostMapping("/upload")
    public Result<Image> upload(@RequestParam("file") MultipartFile file) {
        Image image = messageService.upload(file);
        return Result.success(image);
    }

}
