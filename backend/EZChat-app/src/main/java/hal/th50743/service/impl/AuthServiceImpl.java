package hal.th50743.service.impl;

import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.pojo.*;
import hal.th50743.service.AuthService;
import hal.th50743.service.ChatService;
import hal.th50743.service.FormalUserService;
import hal.th50743.service.UserService;
import hal.th50743.utils.JwtUtils;
import hal.th50743.utils.LoginVOBuilder;
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
    private final ChatService chatService;

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
        if (res != null && res.getUId() != null) {
            log.info("用户登录成功: {}", res.getUId());
            return LoginVOBuilder.build(res.getUId(), res.getUsername(), jwtUtils);
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
        if (req.getUserUId() == null) {
            if (isRegisterReqInvalid(req)) {
                log.warn("注册请求参数不完整");
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Registration info is incomplete");
            }
            // 校验两次密码是否一致
            if (!req.getPassword().equals(req.getConfirmPassword())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Passwords do not match");
            }

            // 1. 创建基础 User 信息
            String avatarObjectName = parseAvatarName(req.getAvatar());
            User userReq = new User(
                    null,
                    null, // uId 由数据库或雪花算法生成
                    req.getNickname(),
                    avatarObjectName,
                    null,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    null
            );

            User userRes = userService.add(userReq);

            // 2. 创建关联的 FormalUser 账号
            FormalUser formalUserReq = new FormalUser(
                    userRes.getId(),
                    req.getUsername(),
                    req.getPassword(), // TODO: 生产环境必须进行 BCrypt 哈希加密
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    null
            );
            formalUserService.add(formalUserReq);

            log.info("新正式用户注册成功: uId={}, username={}", userRes.getUId(), req.getUsername());
            return LoginVOBuilder.build(userRes.getUId(), req.getUsername(), jwtUtils);

        }
        // 情况 B: 临时用户转为正式用户 (已有临时 UId)
        else {
            FormalUser formalUserReq = new FormalUser(
                    null,
                    req.getUsername(),
                    req.getPassword(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    req.getUserUId()
            );

            formalUserService.addByUId(formalUserReq);
            log.info("临时用户转正成功: uId={}, newUsername={}", req.getUserUId(), req.getUsername());
            return LoginVOBuilder.build(req.getUserUId(), req.getUsername(), jwtUtils);
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
        User userReq = new User(
                null,
                null,
                guestReq.getNickname(),
                null, // TODO: 可根据性别或随机数分配默认头像
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null);

        User userRes = userService.add(userReq);

        // 2. 执行自动加入聊天室逻辑
        JoinChatReq joinChatReq = new JoinChatReq(
                guestReq.getChatCode(),
                guestReq.getPassword(),
                userRes.getUId()
        );

        Chat chat = chatService.join(joinChatReq);

        if (chat != null && chat.getChatCode() != null) {
            log.info("访客准入并入群成功: uId={}, chatCode={}", userRes.getUId(), chat.getChatCode());
            // 访客无 username，此处使用 nickname 作为标识构建 Token
            return LoginVOBuilder.build(userRes.getUId(), userRes.getNickname(), jwtUtils);
        } else {
            log.error("访客加入聊天室失败: chatCode={}", guestReq.getChatCode());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Failed to join chat room as guest: chatCode=" + guestReq.getChatCode());
        }
    }
}
