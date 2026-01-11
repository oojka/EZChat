package hal.th50743.controller;

import hal.th50743.pojo.Result;
import hal.th50743.pojo.req.FriendReq;
import hal.th50743.pojo.vo.FriendRequestVO;
import hal.th50743.pojo.vo.FriendVO;
import hal.th50743.service.FriendService;
import hal.th50743.utils.CurrentHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 好友控制器
 * <p>
 * 处理好友系统相关的所有请求，包括：
 * <ul>
 *     <li>好友列表查询</li>
 *     <li>好友申请发送与处理</li>
 *     <li>好友关系维护（删除、备注）</li>
 *     <li>私聊房间创建</li>
 * </ul>
 * <p>
 * 注意：好友系统仅对正式用户开放，访客用户无法使用好友功能。
 *
 * @author EZChat Team
 * @since 2026-01-11
 */
@Slf4j
@RestController
@RequestMapping("/friend")
@RequiredArgsConstructor
public class FriendController {

    /**
     * 好友服务，处理好友相关的业务逻辑
     */
    private final FriendService friendService;

    /**
     * 获取当前用户的好友列表
     * <p>
     * 返回所有已确认的好友关系，包含好友的基本信息和在线状态。
     *
     * @return 包含好友列表的统一响应结果
     */
    @GetMapping("/list")
    public Result<List<FriendVO>> getFriendList() {
        return Result.success(friendService.getFriendList(CurrentHolder.getCurrentId()));
    }

    /**
     * 获取待处理的好友申请列表
     * <p>
     * 返回所有发送给当前用户的待处理好友申请。
     *
     * @return 包含待处理申请列表的统一响应结果
     */
    @GetMapping("/requests")
    public Result<List<FriendRequestVO>> getPendingRequests() {
        return Result.success(friendService.getPendingRequests(CurrentHolder.getCurrentId()));
    }

    /**
     * 发送好友申请
     * <p>
     * 向指定用户发送好友申请，系统会进行以下验证：
     * <ul>
     *     <li>目标用户是否存在</li>
     *     <li>是否已经是好友</li>
     *     <li>是否已发送过待处理的申请</li>
     *     <li>是否尝试添加自己</li>
     * </ul>
     *
     * @param req 好友申请请求，包含目标用户UID
     * @return 统一响应结果
     */
    @PostMapping("/request")
    public Result<Void> sendFriendRequest(@RequestBody FriendReq req) {
        friendService.sendFriendRequest(CurrentHolder.getCurrentId(), req.getTargetUid());
        return Result.success();
    }

    /**
     * 处理好友申请（同意或拒绝）
     * <p>
     * 对收到的好友申请进行处理：
     * <ul>
     *     <li>同意：双方建立好友关系，在 friendships 表中插入双向记录</li>
     *     <li>拒绝：将申请状态标记为已拒绝</li>
     * </ul>
     *
     * @param req 处理请求，包含申请ID和处理结果（accept: true/false）
     * @return 统一响应结果
     */
    @PostMapping("/handle")
    public Result<Void> handleFriendRequest(@RequestBody FriendReq req) {
        friendService.handleFriendRequest(CurrentHolder.getCurrentId(), req.getRequestId(), req.getAccept());
        return Result.success();
    }

    /**
     * 删除好友
     * <p>
     * 解除与指定用户的好友关系，同时删除双向的好友记录。
     * 注意：删除好友不会影响已存在的私聊记录。
     *
     * @param friendUid 要删除的好友UID
     * @return 统一响应结果
     */
    @DeleteMapping("/{friendUid}")
    public Result<Void> removeFriend(@PathVariable String friendUid) {
        friendService.removeFriend(CurrentHolder.getCurrentId(), friendUid);
        return Result.success();
    }

    /**
     * 更新好友备注名
     * <p>
     * 为指定好友设置备注名（别名），仅对当前用户可见。
     *
     * @param req 更新请求，包含好友UID和新的备注名
     * @return 统一响应结果
     */
    @PutMapping("/alias")
    public Result<Void> updateAlias(@RequestBody FriendReq req) {
        friendService.updateAlias(CurrentHolder.getCurrentId(), req.getFriendUid(), req.getAlias());
        return Result.success();
    }

    /**
     * 获取或创建私聊房间
     * <p>
     * 与指定好友发起私聊：
     * <ul>
     *     <li>如果两人之间已存在私聊房间（Type=1），则复用该房间</li>
     *     <li>如果不存在，则自动创建一个新的私聊房间（MaxMembers=2）</li>
     * </ul>
     *
     * @param req 请求对象，包含目标好友UID
     * @return 私聊房间的 chatCode
     */
    @PostMapping("/chat")
    public Result<String> getOrCreatePrivateChat(@RequestBody FriendReq req) {
        String chatCode = friendService.getOrCreatePrivateChat(CurrentHolder.getCurrentId(), req.getTargetUid());
        return Result.success(chatCode);
    }
}
