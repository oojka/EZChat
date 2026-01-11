package hal.th50743.service.impl;

import hal.th50743.assembler.UserAssembler;
import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.mapper.ChatMapper;
import hal.th50743.mapper.ChatMemberMapper;
import hal.th50743.mapper.FriendshipMapper;
import hal.th50743.mapper.UserMapper;
import hal.th50743.pojo.*;
import hal.th50743.service.AssetService;
import hal.th50743.service.UserService;
import hal.th50743.utils.CurrentHolder;
import hal.th50743.service.FormalUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Objects;

import static hal.th50743.utils.UidGenerator.generateUid;

/**
 * 用户服务实现类
 * <p>
 * 负责用户信息的查询、更新、注册及头像上传业务逻辑。
 * <p>
 * 主要功能：
 * <ul>
 *     <li>用户信息查询（支持权限校验：自己/同聊天室成员/好友）</li>
 *     <li>用户信息更新（昵称、头像、简介）</li>
 *     <li>用户类型管理（访客/正式用户）</li>
 *     <li>用户活跃状态更新</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final ChatMemberMapper chatMemberMapper;
    private final ChatMapper chatMapper;
    private final FriendshipMapper friendshipMapper;
    private final AssetService assetService;
    private final UserAssembler userAssembler;
    private final FormalUserService formalUserService;

    @Override
    public UserVO getUserInfoByUid(String uid) {
        User user = userMapper.selectByUidWithAvatar(uid);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Integer reqId = CurrentHolder.getCurrentId();
        Integer targetId = user.getId();

        boolean isSelf = Objects.equals(reqId, targetId);
        boolean isColleague = chatMemberMapper.isValidGetInfoReq(reqId, targetId);
        boolean isFriend = friendshipMapper.selectByUserIdAndFriendId(reqId, targetId) != null;

        if (!isSelf && !isColleague && !isFriend) {
            // [Optional] Allow if searching for stranger to add? 
            // Currently strict: must have some relation.
            // If AddFriendDialog uses this API to preview stranger, it will fail.
            // But AddFriendDialog logic (frontend) just sends request by UID, doesn't preview.
            // So this is fine for now.
            log.warn("[Unauthorized Access] User {} attempted to access info of non-friend/non-member {}", reqId, uid);
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Permission denied: You do not have permission to view this user's profile");
        }

        return userAssembler.toUserVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User add(User user) {
        log.info("Starting new user registration: {}", user.getNickname());
        if (user.getIsDeleted() == null) {
            user.setIsDeleted(0);
        }
        if (user.getUserType() == null) {
            user.setUserType(0);
        }
        for (int i = 1; i <= 5; i++) {
            String randomUid = generateUid(10);
            user.setUid(randomUid);

            try {
                userMapper.insertUser(user);
                log.info("User registration successful: uid={}, internalId={}", user.getUid(), user.getId());
                return user;
            } catch (DuplicateKeyException e) {
                log.warn("UId conflict: {}, retrying attempt {}", randomUid, i);
                if (i == 5) {
                    log.error("Severe UId generation conflict, max retries reached");
                    throw new IllegalStateException("Failed to generate unique user ID after multiple attempts");
                }
            }
        }
        return user;
    }

    @Override
    public Integer getIdByUid(String uid) {
        return userMapper.selectIdByUid(uid);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void update(UserReq userReq) {
        User u = new User();
        u.setId(userReq.getUserId());
        u.setUid(userReq.getUid());
        u.setNickname(userReq.getNickname());
        if (userReq.getAvatar() != null) {
            String avatarObjectName = userReq.getAvatar().getImageName();
            Integer objectId = userAssembler.resolveAvatarId(avatarObjectName);
            if (objectId != null) {
                u.setAssetId(objectId);
                log.debug("Updated user avatar: objectId={}, objectName={}", objectId, avatarObjectName);
            }
        }
        u.setBio(userReq.getBio());
        u.setUpdateTime(LocalDateTime.now());
        log.info("Updating user profile: {}", u);
        userMapper.update(u);
    }

    @Override
    public void updateUserType(Integer userId, Integer userType) {
        if (userId == null || userType == null) {
            log.warn("Failed to update user type: userId or userType is null");
            return;
        }
        userMapper.updateUserType(userId, userType);
    }

    @Override
    public void updateUserDeleted(Integer userId, Integer isDeleted) {
        if (userId == null || isDeleted == null) {
            log.warn("Failed to update user deleted flag: userId or isDeleted is null");
            return;
        }
        userMapper.updateUserDeleted(userId, isDeleted);
    }

    @Override
    public Image uploadAvatar(MultipartFile file) {
        return assetService.uploadAvatar(file);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateLastSeenAt(Integer userId, String currentChatCode, LocalDateTime now) {
        Integer chatId = chatMapper.selectChatIdByChatCode(currentChatCode);
        if (chatId != null) {
            chatMemberMapper.updateLastSeenAt(userId, chatId, now);
        }
        userMapper.updateLastSeenAt(userId, now);
    }

    @Override
    public String getUsernameByUserId(Integer userId) {
        if (userId == null) {
            return null;
        }
        return formalUserService.getUsernameById(userId);
    }

    @Override
    public User getUserById(Integer userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.selectUserById(userId);
    }
}
