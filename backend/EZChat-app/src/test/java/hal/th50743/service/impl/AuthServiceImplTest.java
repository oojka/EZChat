package hal.th50743.service.impl;

import hal.th50743.assembler.UserAssembler;
import hal.th50743.config.CacheProperties;
import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.pojo.Chat;
import hal.th50743.pojo.FormalUser;
import hal.th50743.pojo.FormalUserRegisterReq;
import hal.th50743.pojo.GuestJoinReq;
import hal.th50743.pojo.Image;
import hal.th50743.pojo.JoinChatReq;
import hal.th50743.pojo.LoginReq;
import hal.th50743.pojo.LoginVO;
import hal.th50743.pojo.RefreshTokenReq;
import hal.th50743.pojo.User;
import hal.th50743.pojo.UserReq;
import hal.th50743.service.CacheService;
import hal.th50743.service.ChatService;
import hal.th50743.service.FormalUserService;
import hal.th50743.service.PresenceService;
import hal.th50743.service.UserService;
import hal.th50743.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试
 *
 * <p>覆盖以下场景:</p>
 * <ul>
 *     <li>用户登录 (login): 成功、凭证无效、在线用户强制登出</li>
 *     <li>用户注册 (userRegister): 新用户注册、访客升级为正式用户</li>
 *     <li>访客加入聊天 (joinChat): 邀请码模式、密码模式、参数校验</li>
 *     <li>刷新令牌 (refreshToken): 令牌验证、用户类型区分、在线/离线状态处理</li>
 * </ul>
 *
 * @see AuthServiceImpl
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private FormalUserService formalUserService;

    @Mock
    private UserService userService;

    @Mock
    private ChatService chatService;

    @Mock
    private UserAssembler userAssembler;

    @Mock
    private CacheService cacheService;

    @Mock
    private PresenceService presenceService;

    private CacheProperties cacheProperties;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        cacheProperties = new CacheProperties();
        cacheProperties.setAccessExpireMinutes(5);
        cacheProperties.setFormalRefreshExpireMinutes(10080);
        cacheProperties.setGuestRefreshExpireMinutes(1440);
        authService = new AuthServiceImpl(
                jwtUtils,
                cacheProperties,
                formalUserService,
                userService,
                chatService,
                userAssembler,
                cacheService,
                presenceService
        );
    }

    // ================================
    // 登录测试 (login)
    // ================================

    /**
     * 登录成功 - 验证颁发令牌并缓存
     */
    @Test
    void loginSuccessIssuesTokensAndCaches() {
        LoginReq req = new LoginReq("alice", "pw");
        User user = new User();
        user.setId(1);
        user.setUid("u1");
        user.setUsername("alice");
        when(formalUserService.login(any(LoginReq.class))).thenReturn(user);
        when(jwtUtils.generateJwt(anyMap(), anyLong())).thenReturn("access-token", "refresh-token");

        LoginVO result = authService.login(req);

        assertEquals("u1", result.getUid());
        assertEquals("alice", result.getUsername());
        assertEquals("access-token", result.getAccessToken());
        assertEquals("refresh-token", result.getRefreshToken());
        verify(userService).updateUserType(1, 1);
        verify(cacheService).cacheAccessToken(1, "access-token");
        verify(formalUserService).updateRefreshToken(1, "refresh-token");
    }

    /**
     * 登录失败 - 凭证无效时抛出 INVALID_CREDENTIALS 异常
     */
    @Test
    void loginInvalidCredentialsThrows() {
        LoginReq req = new LoginReq("alice", "pw");
        when(formalUserService.login(any(LoginReq.class))).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));

        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), ex.getCode());
        verify(userService, never()).updateUserType(any(), any());
    }

    /**
     * 登录时用户在线 - 强制登出并清除旧令牌
     */
    @Test
    void loginOnlineForcesLogoutAndClearsTokens() {
        LoginReq req = new LoginReq("alice", "pw");
        User user = new User();
        user.setId(1);
        user.setUid("u1");
        user.setUsername("alice");
        when(formalUserService.login(any(LoginReq.class))).thenReturn(user);
        when(presenceService.isOnline(1)).thenReturn(true);
        when(jwtUtils.generateJwt(anyMap(), anyLong())).thenReturn("access-token", "refresh-token");

        authService.login(req);

        verify(cacheService).evictAccessToken(1);
        verify(formalUserService).clearRefreshToken(1);
    }

    // ================================
    // 用户注册测试 (userRegister)
    // ================================

    /**
     * 新用户注册 - 用户名为空白时抛出 BAD_REQUEST 异常
     */
    @Test
    void userRegisterNewUserInvalidRequestThrows() {
        FormalUserRegisterReq req = new FormalUserRegisterReq();
        req.setUsername(" ");
        req.setPassword("pw");
        req.setNickname("nick");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.userRegister(req));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(userService, never()).add(any(User.class));
    }

    /**
     * 新用户注册 - 两次密码不一致时抛出 BAD_REQUEST 异常
     */
    @Test
    void userRegisterNewUserPasswordMismatchThrows() {
        FormalUserRegisterReq req = new FormalUserRegisterReq();
        req.setUsername("alice");
        req.setPassword("pw1");
        req.setConfirmPassword("pw2");
        req.setNickname("nick");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.userRegister(req));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(userService, never()).add(any(User.class));
    }

    /**
     * 新用户注册成功 - 创建用户记录、正式用户记录并颁发令牌
     */
    @Test
    void userRegisterNewUserSuccessCreatesRecordsAndIssuesTokens() {
        FormalUserRegisterReq req = new FormalUserRegisterReq();
        req.setUsername("alice");
        req.setPassword("pw");
        req.setConfirmPassword("pw");
        req.setNickname("nick");
        req.setAvatar(new Image("avatar.png", null, null, null));
        when(userAssembler.resolveAvatarId("avatar.png")).thenReturn(10);
        User created = new User();
        created.setId(1);
        created.setUid("u1");
        when(userService.add(any(User.class))).thenReturn(created);
        when(jwtUtils.generateJwt(anyMap(), anyLong())).thenReturn("access-token", "refresh-token");

        LoginVO result = authService.userRegister(req);

        assertEquals("u1", result.getUid());
        assertEquals("alice", result.getUsername());
        assertEquals("access-token", result.getAccessToken());
        assertEquals("refresh-token", result.getRefreshToken());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).add(userCaptor.capture());
        assertEquals(Integer.valueOf(1), userCaptor.getValue().getUserType());
        assertEquals(Integer.valueOf(10), userCaptor.getValue().getAssetId());

        ArgumentCaptor<FormalUser> formalCaptor = ArgumentCaptor.forClass(FormalUser.class);
        verify(formalUserService).add(formalCaptor.capture());
        assertEquals(Integer.valueOf(1), formalCaptor.getValue().getUserId());
        assertEquals("alice", formalCaptor.getValue().getUsername());
        verify(cacheService).cacheAccessToken(1, "access-token");
        verify(formalUserService).updateRefreshToken(1, "refresh-token");
    }

    /**
     * 访客升级 - 无附加信息时跳过资料更新
     */
    @Test
    void userRegisterUpgradeNoUpdatesSkipsProfileUpdate() {
        FormalUserRegisterReq req = new FormalUserRegisterReq();
        req.setUserId(2);
        req.setUsername("bob");
        req.setPassword("pw");
        User user = new User();
        user.setId(2);
        user.setUid("u2");
        when(userService.getUserById(2)).thenReturn(user);
        when(jwtUtils.generateJwt(anyMap(), anyLong())).thenReturn("access-token", "refresh-token");

        LoginVO result = authService.userRegister(req);

        assertEquals("u2", result.getUid());
        assertEquals("bob", result.getUsername());
        verify(formalUserService).addByUserId(any(FormalUser.class));
        verify(userService, never()).update(any(UserReq.class));
        verify(userService).updateUserType(2, 1);
    }

    /**
     * 访客升级 - 用户不存在时抛出 USER_NOT_FOUND 异常
     */
    @Test
    void userRegisterUpgradeMissingUserThrows() {
        FormalUserRegisterReq req = new FormalUserRegisterReq();
        req.setUserId(2);
        req.setUsername("bob");
        req.setPassword("pw");
        when(userService.getUserById(2)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.userRegister(req));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(formalUserService).addByUserId(any(FormalUser.class));
        verify(userService, never()).updateUserType(any(), any());
    }

    /**
     * 访客升级成功 - 更新资料并颁发令牌
     */
    @Test
    void userRegisterUpgradeSuccessUpdatesProfileAndIssuesTokens() {
        FormalUserRegisterReq req = new FormalUserRegisterReq();
        req.setUserId(2);
        req.setUsername("bob");
        req.setPassword("pw");
        req.setNickname("newNick");
        req.setAvatar(new Image("avatar.png", null, null, null));
        req.setBio("bio");
        User user = new User();
        user.setId(2);
        user.setUid("u2");
        when(userService.getUserById(2)).thenReturn(user);
        when(jwtUtils.generateJwt(anyMap(), anyLong())).thenReturn("access-token", "refresh-token");

        LoginVO result = authService.userRegister(req);

        assertEquals("u2", result.getUid());
        assertEquals("bob", result.getUsername());

        ArgumentCaptor<UserReq> updateCaptor = ArgumentCaptor.forClass(UserReq.class);
        verify(userService).update(updateCaptor.capture());
        assertEquals(Integer.valueOf(2), updateCaptor.getValue().getUserId());
        assertEquals("newNick", updateCaptor.getValue().getNickname());
        verify(userService).updateUserType(2, 1);
        verify(cacheService).cacheAccessToken(2, "access-token");
        verify(formalUserService).updateRefreshToken(2, "refresh-token");
    }

    // ================================
    // 访客加入聊天测试 (joinChat)
    // ================================

    /**
     * 加入聊天 - 昵称为空白时抛出 BAD_REQUEST 异常
     */
    @Test
    void joinChatMissingNicknameThrows() {
        GuestJoinReq req = new GuestJoinReq();
        req.setNickName(" ");
        req.setAvatar(new Image("avatar.png", null, null, null));

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.joinChat(req));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    /**
     * 加入聊天 - 头像为空时抛出 BAD_REQUEST 异常
     */
    @Test
    void joinChatMissingAvatarThrows() {
        GuestJoinReq req = new GuestJoinReq();
        req.setNickName("guest");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.joinChat(req));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    /**
     * 加入聊天 - 未提供加入方式时抛出 BAD_REQUEST 异常
     */
    @Test
    void joinChatMissingModeThrows() {
        GuestJoinReq req = new GuestJoinReq();
        req.setNickName("guest");
        req.setAvatar(new Image("avatar.png", null, null, null));

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.joinChat(req));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    /**
     * 加入聊天 - 同时提供密码和邀请码时抛出 BAD_REQUEST 异常
     */
    @Test
    void joinChatBothModesThrows() {
        GuestJoinReq req = new GuestJoinReq();
        req.setChatCode("12345678");
        req.setPassword("pw");
        req.setInviteCode("invite123");
        req.setNickName("guest");
        req.setAvatar(new Image("avatar.png", null, null, null));

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.joinChat(req));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    /**
     * 邀请码模式加入成功 - 创建访客用户并颁发令牌
     */
    @Test
    void joinChatInviteModeSuccessIssuesTokens() {
        GuestJoinReq req = new GuestJoinReq();
        req.setInviteCode("invite123");
        req.setNickName("guest");
        req.setAvatar(new Image("avatar.png", null, null, null));
        when(userAssembler.resolveAvatarId("avatar.png")).thenReturn(11);
        User user = new User();
        user.setId(3);
        user.setUid("g1");
        when(userService.add(any(User.class))).thenReturn(user);
        Chat chat = new Chat();
        chat.setChatCode("12345678");
        when(chatService.join(any(JoinChatReq.class))).thenReturn(chat);
        when(jwtUtils.generateJwt(anyMap(), anyLong())).thenReturn("access-token", "refresh-token");

        LoginVO result = authService.joinChat(req);

        assertEquals("g1", result.getUid());
        assertEquals("guest", result.getUsername());
        verify(cacheService).cacheAccessToken(3, "access-token");
        verify(cacheService).cacheGuestRefreshToken(3, "refresh-token");

        ArgumentCaptor<JoinChatReq> joinCaptor = ArgumentCaptor.forClass(JoinChatReq.class);
        verify(chatService).join(joinCaptor.capture());
        assertEquals("invite123", joinCaptor.getValue().getInviteCode());
        assertEquals(Integer.valueOf(3), joinCaptor.getValue().getUserId());
        assertNull(joinCaptor.getValue().getChatCode());
    }

    /**
     * 加入聊天 - 直接使用头像的 assetId 而非解析 imageName
     */
    @Test
    void joinChatUsesAvatarAssetIdDirectly() {
        GuestJoinReq req = new GuestJoinReq();
        req.setInviteCode("invite123");
        req.setNickName("guest");
        req.setAvatar(new Image(null, null, null, 99));
        User user = new User();
        user.setId(3);
        user.setUid("g1");
        when(userService.add(any(User.class))).thenReturn(user);
        Chat chat = new Chat();
        chat.setChatCode("12345678");
        when(chatService.join(any(JoinChatReq.class))).thenReturn(chat);
        when(jwtUtils.generateJwt(anyMap(), anyLong())).thenReturn("access-token", "refresh-token");

        authService.joinChat(req);

        verify(userAssembler, never()).resolveAvatarId(anyString());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).add(userCaptor.capture());
        assertEquals(Integer.valueOf(99), userCaptor.getValue().getAssetId());
    }

    /**
     * 访客在线时加入 - 强制登出并清除旧令牌
     */
    @Test
    void joinChatOnlineGuestForcesLogout() {
        GuestJoinReq req = new GuestJoinReq();
        req.setInviteCode("invite123");
        req.setNickName("guest");
        req.setAvatar(new Image("avatar.png", null, null, null));
        when(userAssembler.resolveAvatarId("avatar.png")).thenReturn(11);
        User user = new User();
        user.setId(3);
        user.setUid("g1");
        when(userService.add(any(User.class))).thenReturn(user);
        when(presenceService.isOnline(3)).thenReturn(true);
        Chat chat = new Chat();
        chat.setChatCode("12345678");
        when(chatService.join(any(JoinChatReq.class))).thenReturn(chat);
        when(jwtUtils.generateJwt(anyMap(), anyLong())).thenReturn("access-token", "refresh-token");

        authService.joinChat(req);

        verify(cacheService).evictAccessToken(3);
        verify(cacheService).evictGuestRefreshToken(3);
    }

    /**
     * 加入聊天 - ChatService 返回 null 时抛出 SYSTEM_ERROR 异常
     */
    @Test
    void joinChatChatServiceReturnsNullThrows() {
        GuestJoinReq req = new GuestJoinReq();
        req.setChatCode("12345678");
        req.setPassword("pw");
        req.setNickName("guest");
        req.setAvatar(new Image("avatar.png", null, null, null));
        when(userAssembler.resolveAvatarId("avatar.png")).thenReturn(11);
        User user = new User();
        user.setId(3);
        user.setUid("g1");
        when(userService.add(any(User.class))).thenReturn(user);
        when(chatService.join(any(JoinChatReq.class))).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.joinChat(req));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
    }

    /**
     * 加入聊天 - 返回的 Chat 对象 chatCode 为空时抛出 SYSTEM_ERROR 异常
     */
    @Test
    void joinChatChatCodeNullThrows() {
        GuestJoinReq req = new GuestJoinReq();
        req.setChatCode("12345678");
        req.setPassword("pw");
        req.setNickName("guest");
        req.setAvatar(new Image("avatar.png", null, null, null));
        when(userAssembler.resolveAvatarId("avatar.png")).thenReturn(11);
        User user = new User();
        user.setId(3);
        user.setUid("g1");
        when(userService.add(any(User.class))).thenReturn(user);
        when(chatService.join(any(JoinChatReq.class))).thenReturn(new Chat());

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.joinChat(req));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
    }

    // ================================
    // 刷新令牌测试 (refreshToken)
    // ================================

    /**
     * 刷新令牌 - 请求为空时抛出 UNAUTHORIZED 异常
     */
    @Test
    void refreshTokenMissingThrows() {
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.refreshToken(null));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    /**
     * 刷新令牌 - JWT 解析失败时抛出 UNAUTHORIZED 异常
     */
    @Test
    void refreshTokenParseJwtThrows() {
        RefreshTokenReq req = new RefreshTokenReq("refresh-token");
        when(jwtUtils.parseJwt("refresh-token")).thenThrow(new JwtException("bad"));

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.refreshToken(req));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    /**
     * 刷新令牌 - 令牌类型不是 refresh 时抛出 UNAUTHORIZED 异常
     */
    @Test
    void refreshTokenTypeMismatchThrows() {
        RefreshTokenReq req = new RefreshTokenReq("refresh-token");
        Claims claims = Jwts.claims(Map.<String, Object>of(
                "tokenType", "access",
                "uid", "u1",
                "username", "alice"
        ));
        when(jwtUtils.parseJwt("refresh-token")).thenReturn(claims);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.refreshToken(req));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    /**
     * 刷新令牌 - 根据 uid 查不到用户 ID 时抛出 UNAUTHORIZED 异常
     */
    @Test
    void refreshTokenUserIdNotFoundThrows() {
        RefreshTokenReq req = new RefreshTokenReq("refresh-token");
        Claims claims = Jwts.claims(Map.<String, Object>of(
                "tokenType", "refresh",
                "uid", "u1",
                "username", "alice"
        ));
        when(jwtUtils.parseJwt("refresh-token")).thenReturn(claims);
        when(userService.getIdByUid("u1")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.refreshToken(req));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    /**
     * 刷新令牌 - 用户记录不存在时抛出 UNAUTHORIZED 异常
     */
    @Test
    void refreshTokenUserMissingThrows() {
        RefreshTokenReq req = new RefreshTokenReq("refresh-token");
        Claims claims = Jwts.claims(Map.<String, Object>of(
                "tokenType", "refresh",
                "uid", "u1",
                "username", "alice"
        ));
        when(jwtUtils.parseJwt("refresh-token")).thenReturn(claims);
        when(userService.getIdByUid("u1")).thenReturn(1);
        when(userService.getUserById(1)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.refreshToken(req));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    /**
     * 刷新令牌 - 正式用户离线且令牌不匹配时抛出 UNAUTHORIZED 异常
     */
    @Test
    void refreshTokenFormalOfflineMismatchThrows() {
        RefreshTokenReq req = new RefreshTokenReq("refresh-token");
        Claims claims = Jwts.claims(Map.<String, Object>of(
                "tokenType", "refresh",
                "uid", "u1",
                "username", "alice"
        ));
        when(jwtUtils.parseJwt("refresh-token")).thenReturn(claims);
        when(userService.getIdByUid("u1")).thenReturn(1);
        User user = new User();
        user.setId(1);
        user.setUserType(1);
        when(userService.getUserById(1)).thenReturn(user);
        when(presenceService.isOnline(1)).thenReturn(false);
        when(formalUserService.getRefreshTokenByUserId(1)).thenReturn("other-token");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.refreshToken(req));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    /**
     * 刷新令牌 - 访客离线且令牌不匹配时抛出 UNAUTHORIZED 异常
     */
    @Test
    void refreshTokenGuestOfflineMismatchThrows() {
        RefreshTokenReq req = new RefreshTokenReq("refresh-token");
        Claims claims = Jwts.claims(Map.<String, Object>of(
                "tokenType", "refresh",
                "uid", "u1",
                "username", "guest"
        ));
        when(jwtUtils.parseJwt("refresh-token")).thenReturn(claims);
        when(userService.getIdByUid("u1")).thenReturn(1);
        User user = new User();
        user.setId(1);
        user.setUserType(0);
        when(userService.getUserById(1)).thenReturn(user);
        when(presenceService.isOnline(1)).thenReturn(false);
        when(cacheService.getGuestRefreshToken(1)).thenReturn("other-token");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.refreshToken(req));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        verify(formalUserService, never()).getRefreshTokenByUserId(anyInt());
    }

    /**
     * 正式用户离线刷新成功 - 颁发新 accessToken 并缓存
     */
    @Test
    void refreshTokenFormalOfflineSuccessCachesAccessToken() {
        RefreshTokenReq req = new RefreshTokenReq("refresh-token");
        Claims claims = Jwts.claims(Map.<String, Object>of(
                "tokenType", "refresh",
                "uid", "u1",
                "username", "alice"
        ));
        when(jwtUtils.parseJwt("refresh-token")).thenReturn(claims);
        when(userService.getIdByUid("u1")).thenReturn(1);
        User user = new User();
        user.setId(1);
        user.setUserType(1);
        when(userService.getUserById(1)).thenReturn(user);
        when(presenceService.isOnline(1)).thenReturn(false);
        when(formalUserService.getRefreshTokenByUserId(1)).thenReturn("refresh-token");
        when(jwtUtils.generateJwt(anyMap(), anyLong())).thenReturn("new-access", "new-refresh");

        LoginVO result = authService.refreshToken(req);

        assertEquals("u1", result.getUid());
        assertEquals("alice", result.getUsername());
        assertEquals("new-access", result.getAccessToken());
        assertEquals("refresh-token", result.getRefreshToken());
        verify(cacheService).cacheAccessToken(1, "new-access");
    }

    /**
     * 访客离线刷新成功 - 颁发新 accessToken 并缓存
     */
    @Test
    void refreshTokenGuestOfflineSuccessCachesAccessToken() {
        RefreshTokenReq req = new RefreshTokenReq("refresh-token");
        Claims claims = Jwts.claims(Map.<String, Object>of(
                "tokenType", "refresh",
                "uid", "u1",
                "username", "guest"
        ));
        when(jwtUtils.parseJwt("refresh-token")).thenReturn(claims);
        when(userService.getIdByUid("u1")).thenReturn(1);
        User user = new User();
        user.setId(1);
        user.setUserType(0);
        when(userService.getUserById(1)).thenReturn(user);
        when(presenceService.isOnline(1)).thenReturn(false);
        when(cacheService.getGuestRefreshToken(1)).thenReturn("refresh-token");
        when(jwtUtils.generateJwt(anyMap(), anyLong())).thenReturn("new-access", "new-refresh");

        LoginVO result = authService.refreshToken(req);

        assertEquals("u1", result.getUid());
        assertEquals("guest", result.getUsername());
        assertEquals("new-access", result.getAccessToken());
        assertEquals("refresh-token", result.getRefreshToken());
        verify(cacheService).cacheAccessToken(1, "new-access");
    }

    /**
     * 在线用户刷新 - 跳过令牌匹配检查直接颁发新令牌
     */
    @Test
    void refreshTokenOnlineSkipsMismatchChecks() {
        RefreshTokenReq req = new RefreshTokenReq("refresh-token");
        Claims claims = Jwts.claims(Map.<String, Object>of(
                "tokenType", "refresh",
                "uid", "u1",
                "username", "alice"
        ));
        when(jwtUtils.parseJwt("refresh-token")).thenReturn(claims);
        when(userService.getIdByUid("u1")).thenReturn(1);
        User user = new User();
        user.setId(1);
        user.setUserType(1);
        when(userService.getUserById(1)).thenReturn(user);
        when(presenceService.isOnline(1)).thenReturn(true);
        when(jwtUtils.generateJwt(anyMap(), anyLong())).thenReturn("new-access", "new-refresh");

        LoginVO result = authService.refreshToken(req);

        assertEquals("new-access", result.getAccessToken());
        assertEquals("refresh-token", result.getRefreshToken());
        verify(formalUserService, never()).getRefreshTokenByUserId(anyInt());
        verify(cacheService, never()).getGuestRefreshToken(anyInt());
    }
}
