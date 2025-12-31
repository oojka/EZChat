package hal.th50743.controller;

import hal.th50743.pojo.ChatReq;
import hal.th50743.pojo.Result;
import hal.th50743.service.ChatService;
import hal.th50743.utils.CurrentHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 聊天室控制器
 * <p>
 * 处理聊天室的创建、查询等请求。
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 创建聊天室
     *
     * @param chatReq 聊天室创建请求
     * @return 统一响应结果
     */
    @PostMapping
    public Result createChat(@RequestBody ChatReq chatReq) {

        return Result.success();

    }

    /**
     * 获取聊天室详情
     *
     * @param chatCode 聊天室代码
     * @return 包含聊天室详情的统一响应结果
     */
    @GetMapping("/{chatCode}")
    public Result getChat(@PathVariable String chatCode) {
        Integer userId = CurrentHolder.getCurrentId();
        return Result.success(chatService.getChat(userId, chatCode));
    }

}
