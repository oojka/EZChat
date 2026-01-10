package hal.th50743.service.impl;

import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.mapper.UserMapper;
import hal.th50743.pojo.FormalUser;
import hal.th50743.pojo.LoginReq;
import hal.th50743.pojo.User;
import hal.th50743.service.FormalUserService;
import hal.th50743.utils.PasswordUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 正式用户服务实现类
 * <p>
 * 负责正式用户的注册、登录及转正逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormalUserServiceImpl implements FormalUserService {

    private final UserMapper userMapper;

    /**
     * 添加正式用户
     *
     * @param formalUserReq 正式用户请求对象
     */
    @Override
    public void add(FormalUser formalUserReq) {
        if (formalUserReq.getUsername() != null) {
            formalUserReq.setUsername(formalUserReq.getUsername().toLowerCase());
        }
        userMapper.insertFormalUser(formalUserReq);
    }

    /**
     * 根据用户ID获取用户名（仅限正式用户）
     *
     * @param userId 用户 ID
     * @return 用户名，如果非正式用户返回 null
     */
    @Override
    public String getUsernameById(Integer userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.selectUsernameByUserId(userId);
    }

    /**
     * 根据 UId 添加正式用户（转正）
     *
     * @deprecated 使用 addByUserId 替代，业务逻辑应使用内部 userId 而非外部 userUid
     * @param formalUserReq 正式用户请求对象（包含 userUid）
     */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addByUId(FormalUser formalUserReq) {
        log.info("add user by UId, user={}", formalUserReq);
        User user = userMapper.selectByUid(formalUserReq.getUserUid());
        formalUserReq.setUserId(user.getId());
        add(formalUserReq);
    }

    /**
     * 根据用户ID添加正式用户（转正）
     * <p>
     * 直接使用内部用户ID，不依赖外部标识符。
     *
     * @param formalUserReq 正式用户请求对象（包含 userId）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addByUserId(FormalUser formalUserReq) {
        log.info("Add formal user by userId: userId={}", formalUserReq.getUserId());
        if (formalUserReq.getUserId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "UserId is required for user upgrade");
        }
        add(formalUserReq);
    }

    /**
     * 更新正式用户 RefreshToken
     *
     * @param userId       用户ID
     * @param refreshToken RefreshToken
     */
    @Override
    public void updateRefreshToken(Integer userId, String refreshToken) {
        if (userId == null || refreshToken == null) {
            log.warn("更新正式用户 RefreshToken 失败: userId 或 refreshToken 为空");
            return;
        }
        userMapper.updateFormalUserToken(userId, refreshToken);
    }

    /**
     * 获取正式用户 RefreshToken
     *
     * @param userId 用户ID
     * @return RefreshToken
     */
    @Override
    public String getRefreshTokenByUserId(Integer userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.selectFormalUserTokenByUserId(userId);
    }

    /**
     * 清空正式用户 RefreshToken
     *
     * @param userId 用户ID
     */
    @Override
    public void clearRefreshToken(Integer userId) {
        if (userId == null) {
            log.warn("清空正式用户 RefreshToken 失败: userId 为空");
            return;
        }
        userMapper.updateFormalUserToken(userId, null);
    }

    /**
     * 用户登录
     * <p>
     * 使用 BCrypt 密码哈希验证，确保密码安全性。
     *
     * @param loginReq 登录请求对象
     * @return User 用户对象，如果用户名或密码错误返回 null
     */
    @Override
    public User login(LoginReq loginReq) {
        // 1. 根据用户名查询正式用户信息（包含密码哈希）
        // 统一转为小写匹配
        String username = loginReq.getUsername() != null ? loginReq.getUsername().toLowerCase() : null;
        FormalUser formalUser = userMapper.selectFormalUserByUsername(username);
        if (formalUser == null) {
            log.warn("登录失败: 用户名不存在 - {}", username);
            return null;
        }

        // 2. 使用 PasswordUtils 验证密码
        boolean passwordMatches = PasswordUtils.matches(loginReq.getPassword(), formalUser.getPasswordHash());
        if (!passwordMatches) {
            log.warn("登录失败: 密码错误 - {}", loginReq.getUsername());
            return null;
        }

        // 3. 密码验证通过，查询并返回用户信息
        User user = userMapper.selectUserByUsername(username);
        if (user == null) {
            log.error("登录异常: 用户名存在但用户信息缺失 - {}", username);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "User data inconsistency");
        }

        log.info("登录成功: username={}, uid={}", loginReq.getUsername(), user.getUid());
        return user;
    }

    /**
     * 修改密码
     * <p>
     * 验证旧密码后更新为新密码，并清除 RefreshToken 强制重新登录。
     *
     * @param userId      用户ID
     * @param oldPassword 旧密码（明文）
     * @param newPassword 新密码（明文）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updatePassword(Integer userId, String oldPassword, String newPassword) {
        // 1. 获取当前密码哈希
        String currentHash = getPasswordHashByUserId(userId);
        if (currentHash == null) {
            log.warn("修改密码失败: 用户不是正式用户 - userId={}", userId);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "User is not a formal user");
        }

        // 2. 验证旧密码
        boolean passwordMatches = PasswordUtils.matches(oldPassword, currentHash);
        if (!passwordMatches) {
            log.warn("修改密码失败: 旧密码错误 - userId={}", userId);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Old password is incorrect");
        }

        // 3. 生成新密码哈希并更新
        String newHash = PasswordUtils.encode(newPassword);
        userMapper.updateFormalUserPassword(userId, newHash);
        log.info("密码修改成功: userId={}", userId);

        // 4. 清除 RefreshToken 强制重新登录
        clearRefreshToken(userId);
    }

    /**
     * 根据用户ID获取密码哈希
     *
     * @param userId 用户ID
     * @return 密码哈希
     */
    @Override
    public String getPasswordHashByUserId(Integer userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.selectPasswordHashByUserId(userId);
    }
}
