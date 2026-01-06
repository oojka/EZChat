package hal.th50743.controller;

import hal.th50743.pojo.ChatReq;
import hal.th50743.pojo.ChatMemberVO;
import hal.th50743.pojo.ChatVO;
import hal.th50743.pojo.CreateChatVO;
import hal.th50743.pojo.JoinChatReq;
import hal.th50743.pojo.Result;
import hal.th50743.service.ChatService;
import java.util.List;
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
    public Result<CreateChatVO> createChat(@RequestBody ChatReq chatReq) {
        Integer userId = CurrentHolder.getCurrentId();
        CreateChatVO res = chatService.createChat(userId, chatReq);
        return Result.success(res);

    }

    /**
     * 获取聊天室详情
     *
     * @param chatCode 聊天室代码
     * @return 包含聊天室详情的统一响应结果
     */
    @GetMapping("/{chatCode}")
    public Result<ChatVO> getChat(@PathVariable String chatCode) {
        Integer userId = CurrentHolder.getCurrentId();
        return Result.success(chatService.getChat(userId, chatCode));
    }

    /**
     * 获取聊天室成员列表（按 chatCode 懒加载）
     *
     * 业务目的：
     * - refresh 初始化阶段只拉 chatList，成员列表由右侧栏按需请求
     *
     * @param chatCode 聊天室对外 ID
     * @return 统一响应结果（成员列表）
     */
    @GetMapping("/{chatCode}/members")
    public Result<List<ChatMemberVO>> getChatMembers(@PathVariable String chatCode) {
        Integer userId = CurrentHolder.getCurrentId();
        return Result.success(chatService.getChatMemberVOList(userId, chatCode));
    }

    /**
     * 正式用户加入聊天室
     * <p>
     * 业务目的：
     * - 已登录的正式用户加入指定聊天室
     * - 支持密码模式和邀请码模式两种加入方式
     * <p>
     * 业务流程：
     * 1. 验证请求参数和模式互斥性
     * 2. 根据模式处理加入逻辑：
     *    - 密码模式：验证密码并加入
     *    - 邀请码模式：验证邀请码并处理使用次数
     * 3. 将用户加入聊天室
     *
     * @param req 加入请求（包含 chatCode + password 或 inviteCode）
     * @return 统一响应结果
     */
    @PostMapping("/join")
    public Result<Void> joinChat(@RequestBody JoinChatReq req) {
        Integer userId = CurrentHolder.getCurrentId();
        req.setUserId(userId);
        chatService.join(req);
        return Result.success();
    }

}
