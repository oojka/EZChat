package hal.th50743.service.impl;

import hal.th50743.assembler.LoginAssembler;
import hal.th50743.assembler.UserAssembler;
import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.pojo.*;
import hal.th50743.service.AuthService;
import hal.th50743.service.ChatService;
import hal.th50743.service.FormalUserService;
import hal.th50743.service.UserService;
import hal.th50743.utils.JwtUtils;
import hal.th50743.utils.PasswordUtils;
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

    private final JwtUtils jwtUtils;
    private final FormalUserService formalUserService;
    private final UserService userService;
    private final ChatService chatService;
    private final UserAssembler userAssembler;

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
            log.info("User login successful: {}", res.getUid());
            return LoginAssembler.build(res.getUid(), res.getUsername(), jwtUtils);
        }

        log.warn("Login failed: invalid username or password - {}", loginReq.getUsername());
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
        // 情况 A: 纯新用户注册 (无临时 userId)
        if (req.getUserId() == null) {
            if (isRegisterReqInvalid(req)) {
                log.warn("Registration request parameters are incomplete");
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
            Integer objectId = null;
            if (req.getAvatar() != null) {
                objectId = userAssembler.resolveAvatarId(req.getAvatar().getImageName());
            }
            // 使用 setter 方法创建 User 对象（避免构造函数参数顺序问题）
            User userReq = new User();
            userReq.setNickname(req.getNickname());
            userReq.setAssetId(objectId);
            userReq.setBio(req.getBio()); // 设置 bio
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
                    null);
            formalUserService.add(formalUserReq);

            log.info("New formal user registration successful: uid={}, username={}", userRes.getUid(),
                    req.getUsername());
            return LoginAssembler.build(userRes.getUid(), req.getUsername(), jwtUtils);

        }
        // 情况 B: 临时用户转为正式用户 (已有临时 userId)
        else {
            // 使用 BCrypt 加密密码
            String passwordHash = PasswordUtils.encode(req.getPassword());
            FormalUser formalUserReq = new FormalUser(
                    req.getUserId(),
                    req.getUsername(),
                    passwordHash,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    null);

            formalUserService.addByUserId(formalUserReq);

            // 获取用户信息以获取uid（用于构建LoginVO和更新检查）
            User user = userService.getUserById(req.getUserId());
            if (user == null || user.getUid() == null) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found for upgrade");
            }

            // 更新用户资料 (Nickname, Avatar, Bio)
            UserReq updateReq = new UserReq();
            updateReq.setUserId(user.getId());
            updateReq.setUid(user.getUid());
            boolean needUpdate = false;

            if (req.getNickname() != null && !req.getNickname().isEmpty()) {
                updateReq.setNickname(req.getNickname());
                needUpdate = true;
            }
            if (req.getAvatar() != null) {
                updateReq.setAvatar(req.getAvatar());
                needUpdate = true;
            }
            if (req.getBio() != null) {
                updateReq.setBio(req.getBio());
                needUpdate = true;
            }

            if (needUpdate) {
                userService.update(updateReq);
                log.info("Updated user profile during upgrade: userId={}", req.getUserId());
            }

            log.info("Temporary user converted to formal user successfully: userId={}, newUsername={}",
                    req.getUserId(), req.getUsername());
            return LoginAssembler.build(user.getUid(), req.getUsername(), jwtUtils);
        }
    }

    /**
     * 访客加入聊天室
     * <p>
     * 支持两种验证模式：
     * 1. 密码模式：chatCode + password + nickName + avatar
     * 2. 邀请码模式：inviteCode + nickName + avatar
     * <p>
     * 业务流程：
     * 1. 验证请求参数
     * 2. 处理头像（关联现有或上传新）
     * 3. 创建用户记录
     * 4. 加入聊天室
     * 5. 生成 JWT token
     *
     * @param req 访客加入请求（包含头像）
     * @return 登录成功后的视图对象（包含 Token）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public LoginVO joinChat(GuestJoinReq req) {
        log.info("Guest attempting to join chat room (with avatar support): nickName={}", req.getNickName());

        // 1. 参数验证
        if (req.getNickName() == null || req.getNickName().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Nickname cannot be empty");
        }

        if (req.getAvatar() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Avatar cannot be empty");
        }

        // 验证模式互斥性
        boolean isPasswordMode = req.getChatCode() != null && req.getPassword() != null;
        boolean isInviteMode = req.getInviteCode() != null;

        if (!isPasswordMode && !isInviteMode) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Must provide chat code and password, or invite code");
        }

        if (isPasswordMode && isInviteMode) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Password mode and invite code mode cannot be used simultaneously");
        }

        // 2. 处理头像
        Integer objectId = null;
        if (req.getAvatar().getAssetId() != null) {
            objectId = req.getAvatar().getAssetId();
        } else {
            objectId = userAssembler.resolveAvatarId(req.getAvatar().getImageName());
        }

        // 3. 创建临时访客用户
        User userReq = new User();
        userReq.setNickname(req.getNickName());
        userReq.setAssetId(objectId);
        userReq.setLastSeenAt(LocalDateTime.now());
        userReq.setCreateTime(LocalDateTime.now());
        userReq.setUpdateTime(LocalDateTime.now());

        User user = userService.add(userReq);
        log.info("Guest user created successfully: uid={}, nickName={}, objectId={}",
                user.getUid(), user.getNickname(), user.getAssetId());

        // 4. 执行加入聊天室逻辑
        // 4. 执行加入聊天室逻辑 (统一调用 ChatService)
        JoinChatReq joinChatReq = new JoinChatReq();
        joinChatReq.setUserId(user.getId());

        if (isInviteMode) {
            // 邀请码模式
            joinChatReq.setInviteCode(req.getInviteCode());
            log.info("Invite mode join request prepared: inviteCode={}, uid={}", req.getInviteCode(), user.getUid());
        } else {
            // 密码模式
            joinChatReq.setChatCode(req.getChatCode());
            joinChatReq.setPassword(req.getPassword());
            log.info("Password mode join request prepared: chatCode={}, uid={}", req.getChatCode(), user.getUid());
        }

        Chat chat = chatService.join(joinChatReq);

        if (chat != null && chat.getChatCode() != null) {
            log.info("Guest joined chat room successfully: uid={}, chatCode={}", user.getUid(), chat.getChatCode());

            // 构建 LoginVO：访客无 username，使用 "guest" 作为标识构建 Token
            return LoginAssembler.build(user.getUid(), "guest", jwtUtils);
        } else {
            log.error("Guest failed to join chat room (ChatService returned null)");
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Failed to join chat room");
        }
    }
}