package hal.th50743.controller;

import hal.th50743.pojo.ChatInviteCreateReq;
import hal.th50743.pojo.ChatInviteVO;
import hal.th50743.pojo.ChatPasswordUpdateReq;
import hal.th50743.pojo.ChatReq;
import hal.th50743.pojo.ChatBasicUpdateReq;
import hal.th50743.pojo.ChatKickReq;
import hal.th50743.pojo.ChatMemberVO;
import hal.th50743.pojo.ChatOwnerTransferReq;
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
 *
 * <p>提供聊天室相关的 RESTful API 端点，包括聊天室的创建、查询、加入、退出、管理等功能。
 *
 * <h3>API 列表</h3>
 * <ul>
 *   <li>POST /chat - 创建聊天室</li>
 *   <li>GET /chat/{chatCode} - 获取聊天室详情</li>
 *   <li>GET /chat/{chatCode}/members - 获取成员列表</li>
 *   <li>GET /chat/{chatCode}/invites - 获取邀请链接列表</li>
 *   <li>POST /chat/{chatCode}/invites - 创建邀请链接</li>
 *   <li>DELETE /chat/{chatCode}/invites/{inviteId} - 撤销邀请链接</li>
 *   <li>POST /chat/join - 加入聊天室</li>
 *   <li>POST /chat/{chatCode}/leave - 退出聊天室</li>
 *   <li>POST /chat/{chatCode}/disband - 解散聊天室</li>
 *   <li>POST /chat/{chatCode}/password - 更新密码</li>
 *   <li>POST /chat/{chatCode}/basic - 更新基础信息</li>
 *   <li>POST /chat/{chatCode}/members/kick - 踢出成员</li>
 *   <li>POST /chat/{chatCode}/owner/transfer - 转让群主</li>
 * </ul>
 *
 * <h3>认证要求</h3>
 * <p>所有接口需要 Header: token
 *
 * @see ChatService
 * @see ChatInviteService
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
     * <p>创建新的群聊房间，创建者自动成为房主。
     *
     * @param chatReq 创建请求（名称、头像、密码等）
     * @return 新创建的聊天室信息
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
     * <p>根据 chatCode 获取聊天室的详细信息，包括名称、头像、成员数等。
     *
     * @param chatCode 聊天室对外唯一标识
     * @return 聊天室详情
     */
    @GetMapping("/{chatCode}")
    public Result<ChatVO> getChat(@PathVariable String chatCode) {
        Integer userId = CurrentHolder.getCurrentId();
        return Result.success(chatService.getChat(userId, chatCode));
    }

    /**
     * 获取聊天室成员列表
     *
     * <p>按需懒加载聊天室成员，用于右侧栏展示成员列表。
     * refresh 初始化阶段只拉取 chatList，成员列表由前端按需请求。
     *
     * @param chatCode 聊天室对外唯一标识
     * @return 成员列表（包含在线状态）
     */
    @GetMapping("/{chatCode}/members")
    public Result<List<ChatMemberVO>> getChatMembers(@PathVariable String chatCode) {
        Integer userId = CurrentHolder.getCurrentId();
        return Result.success(chatService.getChatMemberVOList(userId, chatCode));
    }

    /**
     * 获取聊天室有效邀请链接列表
     *
     * <p>仅群主可调用，用于管理邀请链接（最多 5 个有效链接）。
     *
     * @param chatCode 聊天室对外唯一标识
     * @return 有效的邀请链接列表
     */
    @GetMapping("/{chatCode}/invites")
    public Result<List<ChatInviteVO>> listInvites(@PathVariable String chatCode) {
        Integer userId = CurrentHolder.getCurrentId();
        return Result.success(chatInviteService.listActiveInvites(userId, chatCode));
    }

    /**
     * 创建新的邀请链接
     *
     * <p>仅群主可调用，创建邀请链接供其他用户加入聊天室。
     * 可设置有效期、使用次数等限制。
     *
     * @param chatCode 聊天室对外唯一标识
     * @param req 邀请链接创建请求（有效期、次数限制等）
     * @return 新创建的邀请链接信息
     */
    @PostMapping("/{chatCode}/invites")
    public Result<ChatInviteVO> createInvite(@PathVariable String chatCode, @RequestBody ChatInviteCreateReq req) {
        Integer userId = CurrentHolder.getCurrentId();
        return Result.success(chatInviteService.createInvite(userId, chatCode, req));
    }

    /**
     * 撤销邀请链接
     *
     * <p>仅群主可调用，撤销指定的邀请链接使其立即失效。
     *
     * @param chatCode 聊天室对外唯一标识
     * @param inviteId 邀请链接 ID
     * @return 操作结果
     */
    @DeleteMapping("/{chatCode}/invites/{inviteId}")
    public Result<Void> revokeInvite(@PathVariable String chatCode, @PathVariable Integer inviteId) {
        Integer userId = CurrentHolder.getCurrentId();
        chatInviteService.revokeInvite(userId, chatCode, inviteId);
        return Result.success();
    }

    /**
     * 加入聊天室
     *
     * <p>已登录用户加入指定聊天室，支持两种加入方式：
     * <ul>
     *   <li>密码模式：chatCode + password</li>
     *   <li>邀请码模式：inviteCode</li>
     * </ul>
     *
     * @param req 加入请求（密码模式或邀请码模式）
     * @return 操作结果
     */
    @PostMapping("/join")
    public Result<Void> joinChat(@RequestBody JoinChatReq req) {
        Integer userId = CurrentHolder.getCurrentId();
        req.setUserId(userId);
        chatService.join(req);
        return Result.success();
    }

    /**
     * 退出聊天室
     *
     * <p>当前用户主动退出指定聊天室。群主不能直接退出，需先转让或解散。
     *
     * @param chatCode 聊天室对外唯一标识
     * @return 操作结果
     */
    @PostMapping("/{chatCode}/leave")
    public Result<Void> leaveChat(@PathVariable String chatCode) {
        Integer userId = CurrentHolder.getCurrentId();
        chatService.leaveChat(userId, chatCode);
        return Result.success();
    }

    /**
     * 解散聊天室
     *
     * <p>仅群主可调用，解散聊天室并移除所有成员。此操作不可逆。
     *
     * @param chatCode 聊天室对外唯一标识
     * @return 操作结果
     */
    @PostMapping("/{chatCode}/disband")
    public Result<Void> disbandChat(@PathVariable String chatCode) {
        Integer userId = CurrentHolder.getCurrentId();
        chatService.disbandChat(userId, chatCode);
        return Result.success();
    }

    /**
     * 更新聊天室密码
     *
     * <p>仅群主可调用，更新聊天室的加入密码。可设置新密码或移除密码保护。
     *
     * @param chatCode 聊天室对外唯一标识
     * @param req 密码更新请求（新密码，空则移除密码）
     * @return 操作结果
     */
    @PostMapping("/{chatCode}/password")
    public Result<Void> updateChatPassword(@PathVariable String chatCode, @RequestBody ChatPasswordUpdateReq req) {
        Integer userId = CurrentHolder.getCurrentId();
        chatService.updateChatPassword(userId, chatCode, req);
        return Result.success();
    }

    /**
     * 更新聊天室基础信息
     *
     * <p>仅群主可调用，更新聊天室名称、头像等基础信息。
     *
     * @param chatCode 聊天室对外唯一标识
     * @param req 基础信息更新请求（名称、头像等）
     * @return 更新后的聊天室详情
     */
    @PostMapping("/{chatCode}/basic")
    public Result<ChatVO> updateChatBasicInfo(@PathVariable String chatCode, @RequestBody ChatBasicUpdateReq req) {
        Integer userId = CurrentHolder.getCurrentId();
        return Result.success(chatService.updateChatBasicInfo(userId, chatCode, req));
    }

    /**
     * 批量移除聊天室成员
     *
     * <p>仅群主可调用，将指定成员踢出聊天室。
     *
     * @param chatCode 聊天室对外唯一标识
     * @param req 踢出请求（成员 UID 列表）
     * @return 操作结果
     */
    @PostMapping("/{chatCode}/members/kick")
    public Result<Void> kickMembers(@PathVariable String chatCode, @RequestBody ChatKickReq req) {
        Integer userId = CurrentHolder.getCurrentId();
        chatService.kickMembers(userId, chatCode, req);
        return Result.success();
    }

    /**
     * 转让群主
     *
     * <p>仅群主可调用，将群主身份转让给指定成员。转让后原群主变为普通成员。
     *
     * @param chatCode 聊天室对外唯一标识
     * @param req 转让请求（新群主 UID）
     * @return 操作结果
     */
    @PostMapping("/{chatCode}/owner/transfer")
    public Result<Void> transferOwner(@PathVariable String chatCode, @RequestBody ChatOwnerTransferReq req) {
        Integer userId = CurrentHolder.getCurrentId();
        chatService.transferOwner(userId, chatCode, req);
        return Result.success();
    }

}
