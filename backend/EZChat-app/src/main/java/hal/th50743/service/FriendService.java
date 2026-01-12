package hal.th50743.service;

import hal.th50743.pojo.FriendRequestVO;
import hal.th50743.pojo.FriendVO;

import java.util.List;

/**
 * 好友服务接口
 * <p>
 * 定义好友系统的核心业务逻辑，包括：
 * <ul>
 *     <li>好友列表查询</li>
 *     <li>好友申请发送与处理</li>
 *     <li>好友关系维护</li>
 *     <li>私聊房间创建</li>
 * </ul>
 * <p>
 * 注意：好友功能仅对正式用户开放，访客用户无法使用。
 */
public interface FriendService {

    /**
     * 获取当前用户的好友列表
     * <p>
     * 返回所有已确认的好友关系，包含好友的基本信息。
     *
     * @param currentUserId 当前用户ID
     * @return 好友列表
     */
    List<FriendVO> getFriendList(Integer currentUserId);

    /**
     * 获取待处理的好友申请列表
     * <p>
     * 返回所有发送给当前用户、状态为 PENDING 的好友申请。
     *
     * @param currentUserId 当前用户ID
     * @return 待处理申请列表
     */
    List<FriendRequestVO> getPendingRequests(Integer currentUserId);

    /**
     * 发送好友申请
     * <p>
     * 向指定用户发送好友申请，会进行以下验证：
     * <ul>
     *     <li>目标用户是否存在</li>
     *     <li>是否已经是好友</li>
     *     <li>是否已发送过待处理的申请</li>
     *     <li>是否尝试添加自己</li>
     * </ul>
     *
     * @param currentUserId 当前用户ID
     * @param targetUid     目标用户UID
     */
    void sendFriendRequest(Integer currentUserId, String targetUid);

    /**
     * 处理好友申请（同意或拒绝）
     * <p>
     * 对收到的好友申请进行处理：
     * <ul>
     *     <li>同意：双方建立好友关系，在 friendships 表中插入双向记录</li>
     *     <li>拒绝：将申请状态标记为已拒绝（REJECTED）</li>
     * </ul>
     *
     * @param currentUserId 当前用户ID
     * @param requestId     申请ID
     * @param accept        是否同意（true=同意，false=拒绝）
     */
    void handleFriendRequest(Integer currentUserId, Integer requestId, Boolean accept);

    /**
     * 删除好友
     * <p>
     * 解除与指定用户的好友关系，同时删除双向的好友记录。
     * 注意：删除好友不会影响已存在的私聊记录。
     *
     * @param currentUserId 当前用户ID
     * @param friendUid     要删除的好友UID
     */
    void removeFriend(Integer currentUserId, String friendUid);

    /**
     * 更新好友备注名
     * <p>
     * 为指定好友设置备注名（别名），仅对当前用户可见。
     *
     * @param currentUserId 当前用户ID
     * @param friendUid     好友UID
     * @param alias         新的备注名
     */
    void updateAlias(Integer currentUserId, String friendUid, String alias);

    /**
     * 获取或创建私聊房间
     * <p>
     * 与指定好友发起私聊：
     * <ul>
     *     <li>如果两人之间已存在私聊房间（Type=1），则复用该房间</li>
     *     <li>如果不存在，则自动创建一个新的私聊房间（MaxMembers=2）</li>
     * </ul>
     *
     * @param currentUserId 当前用户ID
     * @param targetUid     目标好友UID
     * @return 私聊房间的 chatCode
     */
    String getOrCreatePrivateChat(Integer currentUserId, String targetUid);
}
