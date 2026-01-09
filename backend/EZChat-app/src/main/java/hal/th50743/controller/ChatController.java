package hal.th50743.controller;

import hal.th50743.pojo.ChatInviteCreateReq;
import hal.th50743.pojo.ChatInviteVO;
import hal.th50743.pojo.ChatPasswordUpdateReq;
import hal.th50743.pojo.ChatReq;
import hal.th50743.pojo.ChatMemberVO;
import hal.th50743.pojo.ChatVO;
import hal.th50743.pojo.CreateChatVO;
import hal.th50743.pojo.JoinChatReq;
import hal.th50743.pojo.Result;
import hal.th50743.service.ChatInviteService;
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
    private final ChatInviteService chatInviteService;

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
     * 获取聊天室有效邀请链接列表（仅群主）
     *
     * 业务目的：
     * - 管理邀请链接（最多 5 个有效链接）
     *
     * @param chatCode 聊天室代码
     * @return 统一响应结果（邀请链接列表）
     */
    @GetMapping("/{chatCode}/invites")
    public Result<List<ChatInviteVO>> listInvites(@PathVariable String chatCode) {
        Integer userId = CurrentHolder.getCurrentId();
        return Result.success(chatInviteService.listActiveInvites(userId, chatCode));
    }

    /**
     * 创建新的邀请链接（仅群主）
     *
     * @param chatCode 聊天室代码
     * @param req 邀请链接创建请求
     * @return 统一响应结果（新创建的邀请链接信息）
     */
    @PostMapping("/{chatCode}/invites")
    public Result<ChatInviteVO> createInvite(@PathVariable String chatCode, @RequestBody ChatInviteCreateReq req) {
        Integer userId = CurrentHolder.getCurrentId();
        return Result.success(chatInviteService.createInvite(userId, chatCode, req));
    }

    /**
     * 撤销邀请链接（仅群主）
     *
     * @param chatCode 聊天室代码
     * @param inviteId 邀请码ID
     * @return 统一响应结果
     */
    @DeleteMapping("/{chatCode}/invites/{inviteId}")
    public Result<Void> revokeInvite(@PathVariable String chatCode, @PathVariable Integer inviteId) {
        Integer userId = CurrentHolder.getCurrentId();
        chatInviteService.revokeInvite(userId, chatCode, inviteId);
        return Result.success();
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

    /**
     * 用户退出聊天室
     *
     * @param chatCode 聊天室代码
     * @return 统一响应结果
     */
    @PostMapping("/{chatCode}/leave")
    public Result<Void> leaveChat(@PathVariable String chatCode) {
        Integer userId = CurrentHolder.getCurrentId();
        chatService.leaveChat(userId, chatCode);
        return Result.success();
    }

    /**
     * 解散聊天室（仅群主可执行）
     *
     * @param chatCode 聊天室代码
     * @return 统一响应结果
     */
    @PostMapping("/{chatCode}/disband")
    public Result<Void> disbandChat(@PathVariable String chatCode) {
        Integer userId = CurrentHolder.getCurrentId();
        chatService.disbandChat(userId, chatCode);
        return Result.success();
    }

    /**
     * 更新聊天室密码（仅群主可执行）
     *
     * @param chatCode 聊天室代码
     * @param req 密码更新请求
     * @return 统一响应结果
     */
    @PostMapping("/{chatCode}/password")
    public Result<Void> updateChatPassword(@PathVariable String chatCode, @RequestBody ChatPasswordUpdateReq req) {
        Integer userId = CurrentHolder.getCurrentId();
        chatService.updateChatPassword(userId, chatCode, req);
        return Result.success();
    }

}
