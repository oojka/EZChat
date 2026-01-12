package hal.th50743.controller;

import hal.th50743.pojo.Result;
import hal.th50743.pojo.FriendReq;
import hal.th50743.pojo.FriendRequestVO;
import hal.th50743.pojo.FriendVO;
import hal.th50743.service.FriendService;
import hal.th50743.utils.CurrentHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 好友控制器
 *
 * <p>提供好友系统相关的 RESTful API 端点，包括好友管理、申请处理、私聊创建等功能。
 *
 * <h3>API 列表</h3>
 * <ul>
 *   <li>GET /friend/list - 获取好友列表</li>
 *   <li>GET /friend/requests - 获取待处理的好友申请</li>
 *   <li>POST /friend/request - 发送好友申请</li>
 *   <li>POST /friend/handle - 处理好友申请</li>
 *   <li>DELETE /friend/{friendUid} - 删除好友</li>
 *   <li>PUT /friend/alias - 更新好友备注</li>
 *   <li>POST /friend/chat - 获取或创建私聊</li>
 * </ul>
 *
 * <h3>认证要求</h3>
 * <p>所有接口需要 Header: token
 *
 * <h3>权限要求</h3>
 * <p>仅正式用户可使用好友功能，访客用户无权限调用。
 *
 * @see FriendService
 */
@Slf4j
@RestController
@RequestMapping("/friend")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    /**
     * 获取好友列表
     *
     * <p>返回当前用户所有已确认的好友，包含好友基本信息和在线状态。
     *
     * @return 好友列表
     */
    @GetMapping("/list")
    public Result<List<FriendVO>> getFriendList() {
        return Result.success(friendService.getFriendList(CurrentHolder.getCurrentId()));
    }

    /**
     * 获取待处理的好友申请
     *
     * <p>返回所有发送给当前用户的待处理好友申请。
     *
     * @return 待处理申请列表
     */
    @GetMapping("/requests")
    public Result<List<FriendRequestVO>> getPendingRequests() {
        return Result.success(friendService.getPendingRequests(CurrentHolder.getCurrentId()));
    }

    /**
     * 发送好友申请
     *
     * <p>向指定用户发送好友申请。系统会验证目标用户是否存在、是否已是好友等。
     *
     * @param req 申请请求（目标用户 UID）
     * @return 操作结果
     */
    @PostMapping("/request")
    public Result<Void> sendFriendRequest(@RequestBody FriendReq req) {
        friendService.sendFriendRequest(CurrentHolder.getCurrentId(), req.getTargetUid());
        return Result.success();
    }

    /**
     * 处理好友申请
     *
     * <p>对收到的好友申请进行处理（同意或拒绝）。
     *
     * @param req 处理请求（申请 ID、accept 标志）
     * @return 操作结果
     */
    @PostMapping("/handle")
    public Result<Void> handleFriendRequest(@RequestBody FriendReq req) {
        friendService.handleFriendRequest(CurrentHolder.getCurrentId(), req.getRequestId(), req.getAccept());
        return Result.success();
    }

    /**
     * 删除好友
     *
     * <p>解除与指定用户的好友关系，同时删除双向记录。
     *
     * @param friendUid 要删除的好友 UID
     * @return 操作结果
     */
    @DeleteMapping("/{friendUid}")
    public Result<Void> removeFriend(@PathVariable String friendUid) {
        friendService.removeFriend(CurrentHolder.getCurrentId(), friendUid);
        return Result.success();
    }

    /**
     * 更新好友备注
     *
     * <p>为指定好友设置备注名（别名），仅对当前用户可见。
     *
     * @param req 更新请求（好友 UID、新备注名）
     * @return 操作结果
     */
    @PutMapping("/alias")
    public Result<Void> updateAlias(@RequestBody FriendReq req) {
        friendService.updateAlias(CurrentHolder.getCurrentId(), req.getFriendUid(), req.getAlias());
        return Result.success();
    }

    /**
     * 获取或创建私聊
     *
     * <p>与指定好友发起私聊。若已存在私聊房间则复用，否则自动创建。
     *
     * @param req 请求（目标好友 UID）
     * @return 私聊房间的 chatCode
     */
    @PostMapping("/chat")
    public Result<String> getOrCreatePrivateChat(@RequestBody FriendReq req) {
        String chatCode = friendService.getOrCreatePrivateChat(CurrentHolder.getCurrentId(), req.getTargetUid());
        return Result.success(chatCode);
    }
}
