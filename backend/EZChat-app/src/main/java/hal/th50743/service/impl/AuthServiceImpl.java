package hal.th50743.service.impl;

import hal.th50743.assembler.LoginAssembler;
import hal.th50743.config.CacheProperties;
import hal.th50743.assembler.UserAssembler;
import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.pojo.*;
import hal.th50743.service.AuthService;
import hal.th50743.service.ChatService;
import hal.th50743.service.FormalUserService;
import hal.th50743.service.PresenceService;
import hal.th50743.service.CacheService;
import hal.th50743.service.UserService;
import hal.th50743.utils.JwtUtils;
import hal.th50743.utils.MessageUtils;
import hal.th50743.utils.PasswordUtils;
import hal.th50743.ws.WebSocketServer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证服务实现类 - 用户认证与授权核心逻辑
 *
 * <h3>职责概述</h3>
 * <p>
 * 负责用户身份认证的全流程处理，包括登录、注册、访客准入和 Token 管理。
 * 作为认证入口，协调用户服务、Token 服务和在线状态服务。
 * </p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li><b>用户登录</b>：验证凭证，签发双 Token（Access + Refresh）</li>
 *     <li><b>用户注册</b>：新用户注册或访客转正式用户</li>
 *     <li><b>访客准入</b>：通过密码或邀请码加入聊天室</li>
 *     <li><b>Token 刷新</b>：RefreshToken 兑换新 AccessToken</li>
 *     <li><b>单点登录</b>：同账号多设备登录时强制下线旧会话</li>
 * </ul>
 *
 * <h3>调用路径</h3>
 * <ul>
 *     <li>{@code AuthController} → 本服务：所有认证 API 入口</li>
 * </ul>
 *
 * <h3>核心不变量</h3>
 * <ul>
 *     <li>AccessToken 有效期短（配置文件定义），存内存缓存</li>
 *     <li>RefreshToken 有效期长，正式用户存 DB，访客存缓存</li>
 *     <li>同账号同时只能一个会话在线，新登录踢掉旧会话</li>
 * </ul>
 *
 * <h3>外部依赖</h3>
 * <ul>
 *     <li><b>JwtUtils</b>：Token 生成与解析</li>
 *     <li><b>TokenCacheService</b>：AccessToken 缓存管理</li>
 *     <li><b>FormalUserService</b>：正式用户凭证验证</li>
 *     <li><b>ChatService</b>：访客加入聊天室</li>
 *     <li><b>PresenceService</b>：在线状态检测</li>
 *     <li><b>WebSocketServer</b>：强制下线通知</li>
 * </ul>
 *
 * <h3>Token 类型说明</h3>
 * <table border="1">
 *     <tr><td>tokenType</td><td>用途</td><td>有效期</td></tr>
 *     <tr><td>access</td><td>API 认证</td><td>短（分钟级）</td></tr>
 *     <tr><td>refresh</td><td>刷新 AccessToken</td><td>长（天级）</td></tr>
 * </table>
 *
 * @author 系统开发者
 * @since 1.0
 * @see AuthController
 * @see CacheService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtUtils jwtUtils;
    private final CacheProperties cacheProperties;
    private final FormalUserService formalUserService;
    private final UserService userService;
    private final ChatService chatService;
    private final UserAssembler userAssembler;
    private final CacheService cacheService;
    private final PresenceService presenceService;

    // ============================================================
    // 1. 辅助工具方法 (Helpers)
    // ============================================================

    /**
     * 构建并缓存双 Token（Access + Refresh）
     *
     * <p>根据用户类型决定 RefreshToken 的存储位置：
     * <ul>
     *     <li>正式用户：存入数据库 formal_users 表</li>
     *     <li>访客：存入 Caffeine 缓存</li>
     * </ul>
     *
     * @param uid      用户 UID
     * @param username 用户名（访客为 "guest"）
     * @param userId   用户内部 ID
     * @param isFormal 是否为正式用户
     * @return 登录视图对象，包含双 Token 和过期时间
     */
    private LoginVO issueTokens(String uid, String username, Integer userId, boolean isFormal) {
        long refreshExpireMinutes = isFormal
                ? cacheProperties.getFormalRefreshExpireMinutes()
                : cacheProperties.getGuestRefreshExpireMinutes();
        LoginVO loginVO = LoginAssembler.build(uid, username, jwtUtils,
                cacheProperties.getAccessExpireMinutes(), refreshExpireMinutes);

        cacheService.cacheAccessToken(userId, loginVO.getAccessToken());

        if (isFormal) {
            formalUserService.updateRefreshToken(userId, loginVO.getRefreshToken());
        } else {
            cacheService.cacheGuestRefreshToken(userId, loginVO.getRefreshToken());
        }

        return loginVO;
    }

    /**
     * 登录前强制下线已在线的同账号会话
     *
     * <p>实现单点登录（Single Sign-On）机制：
     * <ol>
     *     <li>检测账号是否在线</li>
     *     <li>清除旧 Token（缓存和/或数据库）</li>
     *     <li>通过 WebSocket 发送强制下线通知</li>
     *     <li>主动关闭旧的 WebSocket 连接</li>
     * </ol>
     *
     * @param userId   用户内部 ID
     * @param uid      用户 UID
     * @param isFormal 是否为正式用户
     */
    private void forceLogoutIfOnline(Integer userId, String uid, boolean isFormal) {
        if (userId == null || uid == null || uid.isBlank()) {
            log.warn("Force logout failed: userId or uid is null");
            return;
        }
        if (!presenceService.isOnline(userId)) {
            return;
        }

        log.info("Account is online, initiating force logout: userId={}, uid={}", userId, uid);

        // 1) 删除旧 Token
        cacheService.evictAccessToken(userId);
        if (isFormal) {
            formalUserService.clearRefreshToken(userId);
        } else {
            cacheService.evictGuestRefreshToken(userId);
        }

        // 2) 发送强制下线通知
        ForceLogoutBroadcastVO payload = new ForceLogoutBroadcastVO(uid, "LOGIN_ELSEWHERE", LocalDateTime.now());
        String message = MessageUtils.setMessage(2003, "FORCE_LOGOUT", payload);
        WebSocketServer.broadcast(message, List.of(userId));

        // 3) 主动关闭该用户的 WS 连接
        Session session = WebSocketServer.getOnLineUserList().get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.close(new CloseReason(() -> 4003, "FORCE_LOGOUT"));
            } catch (IOException e) {
                log.warn("Failed to close WS for force logout: userId={}, msg={}", userId, e.getMessage());
            }
        }
    }

    /**
     * 校验注册请求参数的合法性
     *
     * @param req 注册请求对象
     * @return 参数无效返回 true，有效返回 false
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
     * <p>验证用户凭证，成功后签发双 Token 并更新用户类型。
     * 若账号已在线则强制下线旧会话。
     *
     * @param loginReq 登录请求（username + password）
     * @return 登录视图对象，包含 accessToken、refreshToken 及过期时间
     * @throws BusinessException INVALID_CREDENTIALS - 用户名或密码错误
     */
    @Override
    public LoginVO login(LoginReq loginReq) {
        User res = formalUserService.login(loginReq);
        if (res != null && res.getUid() != null) {
            log.info("User login successful: {}", res.getUid());
            userService.updateUserType(res.getId(), 1);
            // 登录前检查在线状态并强制下线旧会话
            forceLogoutIfOnline(res.getId(), res.getUid(), true);
            return issueTokens(res.getUid(), res.getUsername(), res.getId(), true);
        }

        log.warn("Login failed: invalid username or password - {}", loginReq.getUsername());
        throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }

    /**
     * 用户注册
     *
     * <p>支持两种场景：
     * <ul>
     *     <li><b>新用户注册</b>：创建 User + FormalUser，签发 Token</li>
     *     <li><b>访客转正</b>：为已存在的临时用户创建 FormalUser 凭证</li>
     * </ul>
     *
     * <h4>注册流程（新用户）</h4>
     * <ol>
     *     <li>参数校验（用户名、密码、昵称必填）</li>
     *     <li>创建 User 记录（生成 UID）</li>
     *     <li>创建 FormalUser 记录（BCrypt 加密密码）</li>
     *     <li>签发双 Token</li>
     * </ol>
     *
     * @param req 注册请求（username、password、nickname、avatar、bio）
     * @return 登录视图对象，包含 Token
     * @throws BusinessException BAD_REQUEST - 参数不完整或密码不匹配
     * @throws BusinessException USER_NOT_FOUND - 访客转正时用户不存在
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
            userReq.setUserType(1);
            userReq.setIsDeleted(0);

            User userRes = userService.add(userReq);

            // 2. 创建关联的 FormalUser 账号（使用 BCrypt 加密密码）
            String passwordHash = PasswordUtils.encode(req.getPassword());
            FormalUser formalUserReq = new FormalUser();
            formalUserReq.setUserId(userRes.getId());
            formalUserReq.setUsername(req.getUsername());
            formalUserReq.setPasswordHash(passwordHash);
            formalUserReq.setCreateTime(LocalDateTime.now());
            formalUserReq.setUpdateTime(LocalDateTime.now());
            formalUserReq.setLastLoginTime(LocalDateTime.now());
            formalUserService.add(formalUserReq);

            log.info("New formal user registration successful: uid={}, username={}", userRes.getUid(),
                    req.getUsername());
            return issueTokens(userRes.getUid(), req.getUsername(), userRes.getId(), true);

        }
        // 情况 B: 临时用户转为正式用户 (已有临时 userId)
        else {
            // 使用 BCrypt 加密密码
            String passwordHash = PasswordUtils.encode(req.getPassword());
            FormalUser formalUserReq = new FormalUser();
            formalUserReq.setUserId(req.getUserId());
            formalUserReq.setUsername(req.getUsername());
            formalUserReq.setPasswordHash(passwordHash);
            formalUserReq.setCreateTime(LocalDateTime.now());
            formalUserReq.setUpdateTime(LocalDateTime.now());
            formalUserReq.setLastLoginTime(LocalDateTime.now());

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
            userService.updateUserType(req.getUserId(), 1);

            log.info("Temporary user converted to formal user successfully: userId={}, newUsername={}",
                    req.getUserId(), req.getUsername());
            return issueTokens(user.getUid(), req.getUsername(), user.getId(), true);
        }
    }

    /**
     * 访客加入聊天室
     *
     * <p>支持两种验证模式（互斥）：
     * <ul>
     *     <li><b>密码模式</b>：chatCode + password + nickName + avatar</li>
     *     <li><b>邀请码模式</b>：inviteCode + nickName + avatar</li>
     * </ul>
     *
     * <h4>业务流程</h4>
     * <ol>
     *     <li>参数校验（昵称、头像必填，验证模式互斥）</li>
     *     <li>处理头像（关联现有或解析新上传）</li>
     *     <li>创建访客用户记录（userType=0）</li>
     *     <li>调用 ChatService.join() 加入聊天室</li>
     *     <li>签发访客 Token（RefreshToken 存缓存）</li>
     * </ol>
     *
     * @param req 访客加入请求
     * @return 登录视图对象，包含 Token
     * @throws BusinessException BAD_REQUEST - 参数校验失败
     * @throws BusinessException SYSTEM_ERROR - 加入聊天室失败
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
        Integer assetId = null;
        if (req.getAvatar().getAssetId() != null) {
            assetId = req.getAvatar().getAssetId();
        } else {
            assetId = userAssembler.resolveAvatarId(req.getAvatar().getImageName());
        }

        // 3. 创建临时访客用户
        User userReq = new User();
        userReq.setNickname(req.getNickName());
        userReq.setAssetId(assetId);
        userReq.setLastSeenAt(LocalDateTime.now());
        userReq.setCreateTime(LocalDateTime.now());
        userReq.setUpdateTime(LocalDateTime.now());
        userReq.setUserType(0);
        userReq.setIsDeleted(0);

        User user = userService.add(userReq);
        log.info("Guest user created successfully: uid={}, nickName={}, assetId={}",
                user.getUid(), user.getNickname(), user.getAssetId());
        // 登录前检查在线状态并强制下线旧会话（访客同样适用）
        forceLogoutIfOnline(user.getId(), user.getUid(), false);

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
            return issueTokens(user.getUid(), "guest", user.getId(), false);
        } else {
            log.error("Guest failed to join chat room (ChatService returned null)");
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Failed to join chat room");
        }
    }

    /**
     * RefreshToken 兑换 AccessToken
     *
     * <p>Token 刷新流程：
     * <ol>
     *     <li>解析并验证 RefreshToken（签名、类型、过期时间）</li>
     *     <li>在服务层统一校验 RefreshToken 是否与存储的一致</li>
     *     <li>签发新的 AccessToken，RefreshToken 保持不变</li>
     *     <li>更新 AccessToken 缓存</li>
     * </ol>
     *
     * <h4>安全机制</h4>
     * <ul>
     *     <li>正式用户：RefreshToken 存 DB，可跨设备校验</li>
     *     <li>访客：RefreshToken 存缓存，重启后失效</li>
     * </ul>
     *
     * @param req RefreshToken 请求
     * @return 新的登录视图对象（仅更新 accessToken）
     * @throws BusinessException UNAUTHORIZED - Token 无效、过期或不匹配
     */
    @Override
    public LoginVO refreshToken(RefreshTokenReq req) {
        if (req == null || req.getRefreshToken() == null || req.getRefreshToken().isBlank()) {
            log.warn("RefreshToken is empty, refusing exchange");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "RefreshToken is required");
        }

        Claims claims;
        try {
            claims = jwtUtils.parseJwt(req.getRefreshToken());
        } catch (JwtException e) {
            log.warn("RefreshToken invalid or expired: {}", e.getMessage());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "RefreshToken is invalid");
        }

        String tokenType = claims.get("tokenType", String.class);
        if (!TOKEN_TYPE_REFRESH.equals(tokenType)) {
            log.warn("RefreshToken type mismatch: {}", tokenType);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid token type");
        }

        String uid = claims.get("uid", String.class);
        String username = claims.get("username", String.class);
        Integer userId = userService.getIdByUid(uid);
        if (userId == null) {
            log.warn("RefreshToken exchange failed: User not found uid={}", uid);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User not found");
        }

        User user = userService.getUserById(userId);
        if (user == null) {
            log.warn("RefreshToken exchange failed: User not found userId={}", userId);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User not found");
        }
        Integer userType = user.getUserType();

        // 在线状态仅用于审计，不影响刷新令牌一致性校验
        boolean online = presenceService.isOnline(userId);
        if (online) {
            log.debug("RefreshToken exchange for online user userId={}, enforcing token match", userId);
        }

        if (userType != null && userType == 1) {
            String storedToken = formalUserService.getRefreshTokenByUserId(userId);
            if (storedToken == null || !storedToken.equals(req.getRefreshToken())) {
                log.warn("RefreshToken exchange failed: Formal user token mismatch userId={}", userId);
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "RefreshToken mismatch");
            }
        } else {
            String cachedToken = cacheService.getGuestRefreshToken(userId);
            if (cachedToken == null || !cachedToken.equals(req.getRefreshToken())) {
                log.warn("RefreshToken exchange failed: Guest token mismatch userId={}", userId);
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "RefreshToken mismatch");
            }
        }

        long refreshExpireMinutes = (userType != null && userType == 1)
                ? cacheProperties.getFormalRefreshExpireMinutes()
                : cacheProperties.getGuestRefreshExpireMinutes();
        LoginVO loginVO = LoginAssembler.build(uid, username, jwtUtils,
                cacheProperties.getAccessExpireMinutes(), refreshExpireMinutes);
        // 保持原 RefreshToken 不变
        loginVO.setRefreshToken(req.getRefreshToken());

        cacheService.cacheAccessToken(userId, loginVO.getAccessToken());

        return loginVO;
    }
}
