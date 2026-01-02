package hal.th50743.service.impl;

import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.mapper.ChatMemberMapper;
import hal.th50743.mapper.ChatInviteMapper;
import hal.th50743.pojo.*;
import hal.th50743.service.AuthService;
import hal.th50743.service.ChatService;
import hal.th50743.service.FileService;
import hal.th50743.service.FormalUserService;
import hal.th50743.service.UserService;
import hal.th50743.utils.JwtUtils;
import hal.th50743.utils.LoginVOBuilder;
import hal.th50743.utils.InviteCodeUtils;
import hal.th50743.utils.PasswordUtils;
import io.minio.MinioOSSOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务实现类
 * <p>
 * 负责用户登录、正式用户注册（含转正）及访客准入逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final MinioOSSOperator minioOSSOperator;
    private final JwtUtils jwtUtils;
    private final FormalUserService formalUserService;
    private final UserService userService;
    private final FileService fileService;
    private final ChatService chatService;
    private final ChatInviteMapper chatInviteMapper;
    private final ChatMemberMapper chatMemberMapper;

    // ============================================================
    // 1. 辅助工具方法 (Helpers)
    // ============================================================

    /**
     * 校验注册请求参数的合法性
     *
     * @param req 注册请求对象
     * @return 如果参数无效返回 true，否则返回 false
     */
    private boolean isRegisterReqInvalid(FormalUserRegisterReq req) {
        return req.getUsername() == null || req.getUsername().trim().isEmpty() ||
                req.getPassword() == null || req.getPassword().trim().isEmpty() ||
                req.getNickname() == null || req.getNickname().trim().isEmpty();
    }

    /**
     * 处理 MinIO 头像路径转换
     *
     * @param avatar 头像对象
     * @return MinIO 对象名
     */
    private String parseAvatarName(Image avatar) {
        if (avatar != null && avatar.getObjectUrl() != null && !avatar.getObjectUrl().isEmpty()) {
            return minioOSSOperator.toObjectName(avatar.getObjectUrl());
        }
        return null;
    }

    // ============================================================
    // 2. 核心认证业务 (Core Business)
    // ============================================================

    /**
     * 用户登录
     *
     * @param loginReq 登录请求对象
     * @return LoginVO 登录成功后的视图对象（包含 Token）
     */
    @Override
    public LoginVO login(LoginReq loginReq) {
        User res = formalUserService.login(loginReq);
        if (res != null && res.getUid() != null) {
            log.info("用户登录成功: {}", res.getUid());
            return LoginVOBuilder.build(res.getUid(), res.getUsername(), jwtUtils);
        }

        log.warn("登录失败: 用户名或密码错误 - {}", loginReq.getUsername());
        throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }

    /**
     * 用户注册
     * <p>
     * 支持新用户注册和临时用户转正。
     *
     * @param req 注册请求对象
     * @return LoginVO 注册成功后的视图对象（包含 Token）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public LoginVO userRegister(FormalUserRegisterReq req) {
        // 情况 A: 纯新用户注册 (无已有临时 UId)
        if (req.getUserUid() == null) {
            if (isRegisterReqInvalid(req)) {
                log.warn("注册请求参数不完整");
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Registration info is incomplete");
            }
            // 校验两次密码是否一致（如果 confirmPassword 不为空）
            // 如果前端未传 confirmPassword，则跳过此校验
            if (req.getConfirmPassword() != null && !req.getConfirmPassword().trim().isEmpty()) {
                if (!req.getPassword().equals(req.getConfirmPassword())) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "Passwords do not match");
                }
            }

            // 1. 创建基础 User 信息
            String avatarObjectName = parseAvatarName(req.getAvatar());
            Integer objectId = null;
            if (avatarObjectName != null) {
                // 查询 objects 表获取 id
                FileEntity objectEntity = fileService.findByObjectName(avatarObjectName);
                if (objectEntity != null) {
                    objectId = objectEntity.getId();
                    log.debug("Set user avatar objectId: {}", objectId);
                } else {
                    log.warn("Avatar object not found during registration: {}", avatarObjectName);
                }
            }
            // 使用 setter 方法创建 User 对象（避免构造函数参数顺序问题）
            User userReq = new User();
            userReq.setNickname(req.getNickname());
            userReq.setObjectId(objectId);
            userReq.setLastSeenAt(LocalDateTime.now());
            userReq.setCreateTime(LocalDateTime.now());
            userReq.setUpdateTime(LocalDateTime.now());

            User userRes = userService.add(userReq);

            // 2. 创建关联的 FormalUser 账号（使用 BCrypt 加密密码）
            String passwordHash = PasswordUtils.encode(req.getPassword());
            FormalUser formalUserReq = new FormalUser(
                    userRes.getId(),
                    req.getUsername(),
                    passwordHash,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    null
            );
            formalUserService.add(formalUserReq);

            log.info("新正式用户注册成功: uid={}, username={}", userRes.getUid(), req.getUsername());
            return LoginVOBuilder.build(userRes.getUid(), req.getUsername(), jwtUtils);

        }
        // 情况 B: 临时用户转为正式用户 (已有临时 UId)
        else {
            // 使用 BCrypt 加密密码
            String passwordHash = PasswordUtils.encode(req.getPassword());
            FormalUser formalUserReq = new FormalUser(
                    null,
                    req.getUsername(),
                    passwordHash,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    req.getUserUid()
            );

            formalUserService.addByUId(formalUserReq);
            log.info("临时用户转正成功: uid={}, newUsername={}", req.getUserUid(), req.getUsername());
            return LoginVOBuilder.build(req.getUserUid(), req.getUsername(), jwtUtils);
        }
    }

    /**
     * 访客登录
     * <p>
     * 创建临时用户并自动加入指定聊天室。
     *
     * @param guestReq 访客请求对象
     * @return LoginVO 登录成功后的视图对象（包含 Token）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public LoginVO guest(GuestReq guestReq) {
        log.info("访客尝试加入聊天室: code={}", guestReq.getChatCode());

        if (guestReq.getNickname() == null || guestReq.getNickname().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Nickname is required for guest access");
        }

        // 1. 创建临时访客用户 (不记录 username 账号，仅记录 nickname)
        // 使用 setter 方法创建 User 对象（避免构造函数参数顺序问题）
        User userReq = new User();
        userReq.setNickname(guestReq.getNickname());
        userReq.setLastSeenAt(LocalDateTime.now());
        userReq.setCreateTime(LocalDateTime.now());
        userReq.setUpdateTime(LocalDateTime.now());

        User userRes = userService.add(userReq);

        // 2. 执行自动加入聊天室逻辑
        JoinChatReq joinChatReq = new JoinChatReq(
                guestReq.getChatCode(),
                guestReq.getPassword(),
                userRes.getUid()
        );

        Chat chat = chatService.join(joinChatReq);

        if (chat != null && chat.getChatCode() != null) {
            log.info("访客准入并入群成功: uid={}, chatCode={}", userRes.getUid(), chat.getChatCode());
            // 访客无 username，此处使用 nickname 作为标识构建 Token
            return LoginVOBuilder.build(userRes.getUid(), userRes.getNickname(), jwtUtils);
        } else {
            log.error("访客加入聊天室失败: chatCode={}", guestReq.getChatCode());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Failed to join chat room as guest: chatCode=" + guestReq.getChatCode());
        }
    }

    /**
     * 邀请码免密加入（访客）
     *
     * @param req 邀请加入请求
     * @return LoginVO（包含 token）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public LoginVO inviteGuest(InviteGuestReq req) {
        if (req == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Request body is required");
        if (req.getChatCode() == null || req.getChatCode().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "chatCode is required");
        }
        if (req.getInviteCode() == null || req.getInviteCode().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "inviteCode is required");
        }
        if (req.getNickname() == null || req.getNickname().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Nickname is required for guest access");
        }

        // 1) 校验房间 join_enabled（关闭加入时，邀请码也不可用）
        ChatJoinInfo joinInfo = chatService.getJoinInfo(req.getChatCode());
        if (joinInfo == null || joinInfo.getChatId() == null) {
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND);
        }
        if (joinInfo.getJoinEnabled() != null && joinInfo.getJoinEnabled() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Join is disabled for this chat");
        }

        // 2) 消费邀请码（原子递增 used_count；过期/撤销/次数用尽都会失败）
        String hash = InviteCodeUtils.sha256Hex(req.getInviteCode());
        int consumed = chatInviteMapper.consume(req.getChatCode(), hash);
        if (consumed <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid or expired invite code");
        }

        // 3) 创建临时用户
        // 使用 setter 方法创建 User 对象（避免构造函数参数顺序问题）
        User userReq = new User();
        userReq.setNickname(req.getNickname());
        userReq.setLastSeenAt(LocalDateTime.now());
        userReq.setCreateTime(LocalDateTime.now());
        userReq.setUpdateTime(LocalDateTime.now());
        User userRes = userService.add(userReq);

        // 4) 免密入群（即使房间设置了密码）
        // 业务原因：邀请码已被消费，代表“免密加入权限”；因此此处不走 chatService.join 的密码校验分支
        chatMemberMapper.insertIgnore(joinInfo.getChatId(), userRes.getId(), LocalDateTime.now());

        // 5) 返回 JWT（访客无 username，用 nickname 作为标识）
        return LoginVOBuilder.build(userRes.getUid(), userRes.getNickname(), jwtUtils);
    }
}
