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
import hal.th50743.pojo.vo.FriendRequestVO;
import hal.th50743.pojo.vo.FriendVO;
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
 * 好友服务实现类
 * <p>
 * 负责好友系统的核心业务逻辑，包括：
 * <ul>
 *     <li>好友列表查询（包含在线状态）</li>
 *     <li>好友申请发送与处理（同意/拒绝）</li>
 *     <li>好友关系维护（删除、备注）</li>
 *     <li>私聊房间创建与复用</li>
 * </ul>
 * <p>
 * 注意：好友功能仅对正式用户开放。
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

    private Image getImageByAssetId(Integer assetId) {
        if (assetId == null) return null;
        Asset asset = assetService.findById(assetId);
        if (asset != null) {
            return assetService.getImageUrlWithAssetId(asset.getAssetName());
        }
        return null;
    }

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

    @Override
    @Transactional
    public void removeFriend(Integer currentUserId, String friendUid) {
        User friend = userMapper.selectUserByUid(friendUid);
        if (friend == null) return;
        friendshipMapper.deleteBiDirectional(currentUserId, friend.getId());
    }

    @Override
    @Transactional
    public void updateAlias(Integer currentUserId, String friendUid, String alias) {
        User friend = userMapper.selectUserByUid(friendUid);
        if (friend == null) return;
        friendshipMapper.updateAlias(currentUserId, friend.getId(), alias);
    }

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
