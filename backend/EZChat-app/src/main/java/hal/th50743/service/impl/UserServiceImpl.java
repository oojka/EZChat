package hal.th50743.service.impl;

import hal.th50743.assembler.UserAssembler;
import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.mapper.ChatMapper;
import hal.th50743.mapper.ChatMemberMapper;
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
 * 负责用户信息的查询、更新、注册及头像上传。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final ChatMemberMapper chatMemberMapper;
    private final ChatMapper chatMapper;
    private final AssetService assetService;
    private final UserAssembler userAssembler;
    private final FormalUserService formalUserService;

    /**
     * 获取用户信息
     * 权限控制：仅限本人或共同群聊成员查看
     *
     * @param uid 用户唯一标识
     * @return UserVO 用户视图对象
     */
    @Override
    public UserVO getUserInfoByUid(String uid) {
        // 使用 JOIN 查询，获取头像 object_name
        User user = userMapper.selectByUidWithAvatar(uid);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Integer reqId = CurrentHolder.getCurrentId();
        Integer targetId = user.getId();

        // 权限校验：1.是否是本人 2.是否有共同群组（防止任意遍历用户信息）
        boolean isSelf = Objects.equals(reqId, targetId);
        boolean isColleague = chatMemberMapper.isValidGetInfoReq(reqId, targetId);

        if (!isSelf && !isColleague) {
            log.warn("[越权访问] 用户 {} 尝试获取非好友/非群员 {} 的信息", reqId, uid);
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Permission denied: You do not have permission to view this user's profile");
        }

        // 使用转换工具类进行 Entity 到 VO 的转换
        return userAssembler.toUserVO(user);
    }

    /**
     * 注册用户：包含 UId 冲突重试机制
     *
     * @param user 用户对象
     * @return User 注册后的用户对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public User add(User user) {
        log.info("开始注册新用户: {}", user.getNickname());
        if (user.getIsDeleted() == null) {
            user.setIsDeleted(0);
        }
        if (user.getUserType() == null) {
            user.setUserType(0);
        }
        // 设置最大重试次数为 5 次
        for (int i = 1; i <= 5; i++) {
            // 生成 10 位随机公开 UId
            String randomUid = generateUid(10);
            user.setUid(randomUid);

            try {
                userMapper.insertUser(user);
                log.info("用户注册成功: uid={}, internalId={}", user.getUid(), user.getId());
                return user; // 插入成功，直接返回结果，结束方法
            } catch (DuplicateKeyException e) {
                log.warn("UId 冲突: {}, 正在进行第 {} 次重试", randomUid, i);

                // 如果是最后一次尝试仍然失败，则抛出异常
                if (i == 5) {
                    log.error("UId 生成严重冲突，已达重试上限");
                    throw new IllegalStateException("Failed to generate unique user ID after multiple attempts");
                }
            }
        }
        return user;
    }

    /**
     * 根据 UId 获取用户 ID
     *
     * @param uid 用户唯一标识
     * @return 用户 ID
     */
    @Override
    public Integer getIdByUid(String uid) {
        return userMapper.selectIdByUid(uid);
    }

    /**
     * 个人资料更新
     *
     * @param userReq 用户更新请求对象
     */
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
            // 逻辑约束：如果对象不存在，不设置 object_id（保持为 NULL 或抛出异常）
            // 注意：resolveAvatarId 已包含判空和日志
            if (objectId != null) {
                u.setAssetId(objectId);
                log.debug("Updated user avatar: objectId={}, objectName={}", objectId, avatarObjectName);
            }
        }
        u.setBio(userReq.getBio());
        u.setUpdateTime(LocalDateTime.now());
        log.info("更新用户资料: {}", u);
        userMapper.update(u);
    }

    /**
     * 更新用户类型
     *
     * @param userId   用户ID
     * @param userType 用户类型
     */
    @Override
    public void updateUserType(Integer userId, Integer userType) {
        if (userId == null || userType == null) {
            log.warn("更新用户类型失败: userId 或 userType 为空");
            return;
        }
        userMapper.updateUserType(userId, userType);
    }

    /**
     * 更新用户删除标记
     *
     * @param userId    用户ID
     * @param isDeleted 删除标记
     */
    @Override
    public void updateUserDeleted(Integer userId, Integer isDeleted) {
        if (userId == null || isDeleted == null) {
            log.warn("更新用户删除标记失败: userId 或 isDeleted 为空");
            return;
        }
        userMapper.updateUserDeleted(userId, isDeleted);
    }

    /**
     * 头像上传至 MinIO
     *
     * @param file 头像文件
     * @return Image 图片对象
     */
    @Override
    public Image uploadAvatar(MultipartFile file) {
        // 业务目的：头像上传统一交给 OSS 媒体服务处理（含图片规范化/缩略图/public 访问）
        return assetService.uploadAvatar(file);
    }

    /**
     * 活跃状态与已读时间同步
     *
     * @param userId          用户ID
     * @param currentChatCode 当前聊天室代码
     * @param now             当前时间
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateLastSeenAt(Integer userId, String currentChatCode, LocalDateTime now) {
        Integer chatId = chatMapper.selectChatIdByChatCode(currentChatCode);
        if (chatId != null) {
            chatMemberMapper.updateLastSeenAt(userId, chatId, now);
        }
        // 同步更新用户表的最后活跃时间
        userMapper.updateLastSeenAt(userId, now);
    }

    /**
     * 根据用户ID获取用户名
     *
     * @param userId 用户ID
     * @return 用户名（正式用户）或 null（访客用户）
     */
    @Override
    public String getUsernameByUserId(Integer userId) {
        if (userId == null) {
            return null;
        }
        // 查询 formal_users 表获取用户名
        return formalUserService.getUsernameById(userId);
    }

    /**
     * 根据用户ID获取用户信息
     *
     * @param userId 用户ID
     * @return 用户对象
     */
    @Override
    public User getUserById(Integer userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.selectUserById(userId);
    }
}
