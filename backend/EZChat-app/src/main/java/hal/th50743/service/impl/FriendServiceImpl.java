package hal.th50743.service.impl;

import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.mapper.ChatMapper;
import hal.th50743.mapper.FriendRequestMapper;
import hal.th50743.mapper.FriendshipMapper;
import hal.th50743.mapper.UserMapper;
import hal.th50743.pojo.Asset;
import hal.th50743.pojo.FriendRequest;
import hal.th50743.pojo.Friendship;
import hal.th50743.pojo.Image;
import hal.th50743.pojo.User;
import hal.th50743.pojo.FriendRequestVO;
import hal.th50743.pojo.FriendVO;
import hal.th50743.service.AssetService;
import hal.th50743.service.ChatService;
import hal.th50743.service.FriendService;
import hal.th50743.service.PresenceService;
import hal.th50743.utils.MessageUtils;
import hal.th50743.ws.WebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 好友服务实现类 - 好友系统核心业务逻辑
 *
 * <h3>职责概述</h3>
 * <p>
 * 负责好友关系的全生命周期管理，包括好友申请、关系维护和私聊房间创建。
 * 作为社交功能的核心服务，与聊天服务紧密协作。
 * </p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li><b>好友列表</b>：查询好友列表（含在线状态、头像、备注）</li>
 *     <li><b>好友申请</b>：发送申请、查询待处理申请、处理申请（同意/拒绝）</li>
 *     <li><b>关系维护</b>：删除好友、修改备注</li>
 *     <li><b>私聊创建</b>：创建或复用私聊房间</li>
 * </ul>
 *
 * <h3>调用路径</h3>
 * <ul>
 *     <li>{@code FriendController} → 本服务：所有好友 API 入口</li>
 * </ul>
 *
 * <h3>核心不变量</h3>
 * <ul>
 *     <li>好友关系为双向：A 添加 B 成功后，双方互为好友</li>
 *     <li>好友功能仅对正式用户开放（userType=1）</li>
 *     <li>不能添加自己为好友</li>
 *     <li>重复申请或已是好友时抛出异常</li>
 * </ul>
 *
 * <h3>外部依赖</h3>
 * <ul>
 *     <li><b>ChatService</b>：创建私聊房间</li>
 *     <li><b>PresenceService</b>：查询好友在线状态</li>
 *     <li><b>AssetService</b>：获取好友头像</li>
 *     <li><b>WebSocketServer</b>：好友申请实时通知</li>
 * </ul>
 *
 * <h3>好友申请状态</h3>
 * <table border="1">
 *     <tr><td>0</td><td>Pending（待处理）</td></tr>
 *     <tr><td>1</td><td>Accepted（已同意）</td></tr>
 *     <tr><td>2</td><td>Rejected（已拒绝）</td></tr>
 * </table>
 *
 * @author 系统开发者
 * @since 1.0
 * @see FriendController
 * @see ChatService#createPrivateChat
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final FriendshipMapper friendshipMapper;
    private final FriendRequestMapper friendRequestMapper;
    private final UserMapper userMapper;
    private final AssetService assetService;
    private final PresenceService presenceService;
    private final ChatMapper chatMapper;
    private final ChatService chatService;

    /**
     * 根据 assetId 获取头像 Image 对象
     *
     * @param assetId 资产 ID
     * @return Image 对象（包含 URL），不存在返回 null
     */
    private Image getImageByAssetId(Integer assetId) {
        if (assetId == null) return null;
        Asset asset = assetService.findById(assetId);
        if (asset != null) {
            return assetService.getImageUrlWithAssetId(asset.getAssetName());
        }
        return null;
    }

    /**
     * 获取当前用户的好友列表
     *
     * <p>返回所有好友的详细信息，包含在线状态、头像、备注等。
     *
     * @param currentUserId 当前用户 ID
     * @return 好友 VO 列表，无好友时返回空列表
     */
    @Override
    public List<FriendVO> getFriendList(Integer currentUserId) {
        List<Friendship> friendships = friendshipMapper.selectByUserId(currentUserId);
        if (friendships.isEmpty()) {
            return new ArrayList<>();
        }

        List<FriendVO> vos = new ArrayList<>();
        for (Friendship fs : friendships) {
            User friend = userMapper.selectUserById(fs.getFriendId());
            if (friend != null) {
                FriendVO vo = new FriendVO();
                vo.setUserId(friend.getId());
                vo.setUid(friend.getUid());
                vo.setNickname(friend.getNickname());
                vo.setAlias(fs.getAlias());
                vo.setBio(friend.getBio());
                vo.setCreateTime(fs.getCreateTime());
                vo.setLastSeenAt(friend.getLastSeenAt());
                vo.setAvatar(getImageByAssetId(friend.getAssetId()));
                vo.setOnline(presenceService.isOnline(friend.getId()));
                vos.add(vo);
            }
        }
        return vos;
    }

    /**
     * 获取待处理的好友申请列表
     *
     * <p>查询发送给当前用户且状态为 Pending（0）的申请。
     *
     * @param currentUserId 当前用户 ID
     * @return 好友申请 VO 列表
     */
    @Override
    public List<FriendRequestVO> getPendingRequests(Integer currentUserId) {
        List<FriendRequest> requests = friendRequestMapper.selectPendingByReceiverId(currentUserId);
        List<FriendRequestVO> vos = new ArrayList<>();
        
        for (FriendRequest req : requests) {
            User sender = userMapper.selectUserById(req.getSenderId());
            if (sender != null) {
                FriendRequestVO vo = new FriendRequestVO();
                vo.setId(req.getId());
                vo.setSenderId(sender.getId());
                vo.setSenderUid(sender.getUid());
                vo.setSenderNickname(sender.getNickname());
                vo.setStatus(req.getStatus());
                vo.setCreateTime(req.getCreateTime());
                vo.setSenderAvatar(getImageByAssetId(sender.getAssetId()));
                vos.add(vo);
            }
        }
        return vos;
    }

    /**
     * 发送好友申请
     *
     * <p>业务规则：
     * <ul>
     *     <li>不能添加自己</li>
     *     <li>已是好友不能重复添加</li>
     *     <li>已有待处理申请不能重复发送</li>
     *     <li>若对方已向自己发送申请，则自动同意（双向确认）</li>
     * </ul>
     *
     * <p>发送成功后通过 WebSocket 通知目标用户。
     *
     * @param currentUserId 当前用户 ID
     * @param targetUid     目标用户 UID
     * @throws BusinessException USER_NOT_FOUND - 目标用户不存在
     * @throws BusinessException BAD_REQUEST - 不能添加自己、已是好友、申请已发送
     */
    @Override
    @Transactional
    public void sendFriendRequest(Integer currentUserId, String targetUid) {
        User target = userMapper.selectUserByUid(targetUid);
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found");
        }
        if (target.getId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot add yourself");
        }
        
        Friendship existing = friendshipMapper.selectByUserIdAndFriendId(currentUserId, target.getId());
        if (existing != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Already friends");
        }
        
        FriendRequest pending = friendRequestMapper.selectPendingBySenderAndReceiver(currentUserId, target.getId());
        if (pending != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request already sent");
        }
        
        FriendRequest reversePending = friendRequestMapper.selectPendingBySenderAndReceiver(target.getId(), currentUserId);
        if (reversePending != null) {
            handleFriendRequest(currentUserId, reversePending.getId(), true);
            return;
        }

        // Create Request
        FriendRequest req = new FriendRequest();
        req.setSenderId(currentUserId);
        req.setReceiverId(target.getId());
        friendRequestMapper.insert(req); // id is generated

        // Broadcast to target
        try {
            User sender = userMapper.selectUserById(currentUserId);
            FriendRequestVO vo = new FriendRequestVO();
            vo.setId(req.getId());
            vo.setSenderId(sender.getId());
            vo.setSenderUid(sender.getUid());
            vo.setSenderNickname(sender.getNickname());
            vo.setStatus(0); // Pending
            vo.setCreateTime(LocalDateTime.now());
            vo.setSenderAvatar(getImageByAssetId(sender.getAssetId()));

            String message = MessageUtils.setMessage(5001, "FRIEND_REQUEST", vo);
            WebSocketServer.broadcast(message, Collections.singletonList(target.getId()));
        } catch (Exception e) {
            log.error("Failed to broadcast friend request", e);
        }
    }

    /**
     * 处理好友申请（同意或拒绝）
     *
     * <p>同意申请后创建双向好友关系。
     *
     * @param currentUserId 当前用户 ID（必须是申请的接收者）
     * @param requestId     申请 ID
     * @param accept        是否同意：true=同意，false=拒绝
     * @throws BusinessException BAD_REQUEST - 申请不存在或已处理
     * @throws BusinessException FORBIDDEN - 不是申请的接收者
     */
    @Override
    @Transactional
    public void handleFriendRequest(Integer currentUserId, Integer requestId, Boolean accept) {
        FriendRequest req = friendRequestMapper.selectById(requestId);
        if (req == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request not found");
        }
        if (!req.getReceiverId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Not your request");
        }
        if (req.getStatus() != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request already handled");
        }

        if (accept) {
            friendRequestMapper.updateStatus(requestId, 1);
            
            Friendship f1 = new Friendship();
            f1.setUserId(req.getSenderId());
            f1.setFriendId(req.getReceiverId());
            friendshipMapper.insert(f1);
            
            Friendship f2 = new Friendship();
            f2.setUserId(req.getReceiverId());
            f2.setFriendId(req.getSenderId());
            friendshipMapper.insert(f2);
        } else {
            friendRequestMapper.updateStatus(requestId, 2);
        }
    }

    /**
     * 删除好友
     *
     * <p>删除双向好友关系。
     *
     * @param currentUserId 当前用户 ID
     * @param friendUid     好友 UID
     */
    @Override
    @Transactional
    public void removeFriend(Integer currentUserId, String friendUid) {
        User friend = userMapper.selectUserByUid(friendUid);
        if (friend == null) return;
        friendshipMapper.deleteBiDirectional(currentUserId, friend.getId());
    }

    /**
     * 更新好友备注
     *
     * @param currentUserId 当前用户 ID
     * @param friendUid     好友 UID
     * @param alias         新备注名
     */
    @Override
    @Transactional
    public void updateAlias(Integer currentUserId, String friendUid, String alias) {
        User friend = userMapper.selectUserByUid(friendUid);
        if (friend == null) return;
        friendshipMapper.updateAlias(currentUserId, friend.getId(), alias);
    }

    /**
     * 获取或创建私聊房间
     *
     * <p>基于好友关系的 1v1 私聊。若已存在私聊房间则返回其 chatCode，
     * 否则调用 ChatService 创建新的私聊房间。
     *
     * @param currentUserId 当前用户 ID
     * @param targetUid     目标好友 UID
     * @return 私聊房间的 chatCode
     * @throws BusinessException USER_NOT_FOUND - 目标用户不存在
     */
    @Override
    @Transactional
    public String getOrCreatePrivateChat(Integer currentUserId, String targetUid) {
        User target = userMapper.selectUserByUid(targetUid);
        if (target == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found");
        
        String existingChatCode = chatMapper.selectPrivateChatCodeBetweenUsers(currentUserId, target.getId());
        if (existingChatCode != null) {
            return existingChatCode;
        }
        
        return chatService.createPrivateChat(currentUserId, target.getId());
    }
}
