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
 * 用户服务实现类 - 用户核心业务逻辑处理
 *
 * <h3>职责概述</h3>
 * <p>
 * 负责用户全生命周期管理，包括用户注册、信息查询、资料更新和状态维护。
 * 作为用户领域的核心服务，被多个上游服务依赖。
 * </p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li><b>用户注册</b>：生成唯一 UID，创建用户记录（含重试机制）</li>
 *     <li><b>信息查询</b>：支持权限校验（自己/同聊天室成员/好友）</li>
 *     <li><b>资料更新</b>：昵称、头像、简介等个人信息维护</li>
 *     <li><b>状态管理</b>：用户类型（访客/正式）、删除标记、最后活跃时间</li>
 *     <li><b>头像上传</b>：委托 AssetService 处理（含图片规范化）</li>
 * </ul>
 *
 * <h3>调用路径</h3>
 * <ul>
 *     <li>{@code UserController} → 本服务：用户资料接口</li>
 *     <li>{@code AuthService} → 本服务：注册/登录时创建和查询用户</li>
 *     <li>{@code WebSocketServer} → 本服务：连接断开时更新最后活跃时间</li>
 *     <li>{@code FriendService} → 本服务：获取好友用户信息</li>
 * </ul>
 *
 * <h3>核心不变量</h3>
 * <ul>
 *     <li>UID 全局唯一且不可变（10位随机字符串）</li>
 *     <li>用户信息查询需权限校验：仅自己、同聊天室成员或好友可访问</li>
 *     <li>用户类型：0=访客，1=正式用户</li>
 * </ul>
 *
 * <h3>外部依赖</h3>
 * <ul>
 *     <li><b>AssetService</b>：头像上传与存储</li>
 *     <li><b>FormalUserService</b>：正式用户凭证查询</li>
 *     <li><b>MySQL</b>：用户数据持久化（users 表）</li>
 * </ul>
 *
 * @author 系统开发者
 * @since 1.0
 * @see UserController
 * @see AuthService
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

    /**
     * 根据 UID 获取用户详细信息
     *
     * <p>包含权限校验逻辑：仅允许查询自己、同聊天室成员或好友的信息。
     *
     * @param uid 用户对外唯一标识（10位字符串）
     * @return 用户信息 VO，包含 uid、nickname、avatar、bio 等
     * @throws BusinessException USER_NOT_FOUND - 用户不存在
     * @throws BusinessException FORBIDDEN - 无权查看该用户信息
     */
    @Override
    public UserVO getUserInfoByUid(String uid) {
        User user = userMapper.selectByUidWithAvatar(uid);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Integer reqId = CurrentHolder.getCurrentId();
        Integer targetId = user.getId();

        // 权限校验：必须满足以下任一条件才允许查询
        boolean isSelf = Objects.equals(reqId, targetId);
        boolean isColleague = chatMemberMapper.isValidGetInfoReq(reqId, targetId);
        boolean isFriend = friendshipMapper.selectByUserIdAndFriendId(reqId, targetId) != null;

        if (!isSelf && !isColleague && !isFriend) {
            log.warn("[Unauthorized Access] User {} attempted to access info of non-friend/non-member {}", reqId, uid);
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Permission denied: You do not have permission to view this user's profile");
        }

        return userAssembler.toUserVO(user);
    }

    /**
     * 创建新用户
     *
     * <p>自动生成唯一 UID（10位随机字符串），若发生唯一键冲突则重试最多 5 次。
     *
     * <h4>默认值处理</h4>
     * <ul>
     *     <li>isDeleted：默认 0（未删除）</li>
     *     <li>userType：默认 0（访客）</li>
     * </ul>
     *
     * @param user 用户实体（nickname 必填，uid 自动生成）
     * @return 创建后的用户实体，包含生成的 id 和 uid
     * @throws IllegalStateException UID 生成重试 5 次后仍冲突
     */
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
        // UID 生成策略：随机 10 位字符串，冲突时重试最多 5 次
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

    /**
     * 根据 UID 获取用户内部 ID
     *
     * @param uid 用户对外唯一标识
     * @return 用户数据库主键 ID，不存在返回 null
     */
    @Override
    public Integer getIdByUid(String uid) {
        return userMapper.selectIdByUid(uid);
    }

    /**
     * 更新用户资料
     *
     * <p>支持更新昵称、头像和简介。头像通过 assetName 解析为 assetId 后关联。
     *
     * @param userReq 用户更新请求，包含 userId、nickname、avatar、bio
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void update(UserReq userReq) {
        User u = new User();
        u.setId(userReq.getUserId());
        u.setUid(userReq.getUid());
        u.setNickname(userReq.getNickname());
        if (userReq.getAvatar() != null) {
            // 头像解析：通过 assetName 查找已上传的 assetId
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

    /**
     * 更新用户类型
     *
     * @param userId   用户 ID
     * @param userType 用户类型：0=访客，1=正式用户
     */
    @Override
    public void updateUserType(Integer userId, Integer userType) {
        if (userId == null || userType == null) {
            log.warn("Failed to update user type: userId or userType is null");
            return;
        }
        userMapper.updateUserType(userId, userType);
    }

    /**
     * 更新用户删除标记（软删除）
     *
     * @param userId    用户 ID
     * @param isDeleted 删除标记：0=正常，1=已删除
     */
    @Override
    public void updateUserDeleted(Integer userId, Integer isDeleted) {
        if (userId == null || isDeleted == null) {
            log.warn("Failed to update user deleted flag: userId or isDeleted is null");
            return;
        }
        userMapper.updateUserDeleted(userId, isDeleted);
    }

    /**
     * 上传用户头像
     *
     * <p>委托 AssetService 处理，包含图片规范化和 MinIO 上传。
     *
     * @param file 头像文件（支持 JPEG/PNG/GIF）
     * @return 图片对象，包含 assetName、url、thumbUrl、assetId
     */
    @Override
    public Image uploadAvatar(MultipartFile file) {
        return assetService.uploadAvatar(file);
    }

    /**
     * 更新用户最后活跃时间
     *
     * <p>同时更新 chat_members 表（特定聊天室）和 users 表（全局）。
     * 由 WebSocket 断开连接时调用。
     *
     * @param userId          用户 ID
     * @param currentChatCode 最后活跃的聊天室代码（可为 null）
     * @param now             活跃时间
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateLastSeenAt(Integer userId, String currentChatCode, LocalDateTime now) {
        Integer chatId = chatMapper.selectChatIdByChatCode(currentChatCode);
        if (chatId != null) {
            chatMemberMapper.updateLastSeenAt(userId, chatId, now);
        }
        userMapper.updateLastSeenAt(userId, now);
    }

    /**
     * 根据用户 ID 获取用户名
     *
     * <p>仅正式用户有用户名，访客返回 null。
     *
     * @param userId 用户 ID
     * @return 用户名，访客或不存在返回 null
     */
    @Override
    public String getUsernameByUserId(Integer userId) {
        if (userId == null) {
            return null;
        }
        return formalUserService.getUsernameById(userId);
    }

    /**
     * 根据用户 ID 获取用户实体
     *
     * @param userId 用户 ID
     * @return 用户实体，不存在返回 null
     */
    @Override
    public User getUserById(Integer userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.selectUserById(userId);
    }
}
