package hal.th50743.assembler;

import hal.th50743.pojo.LoginVO;
import hal.th50743.utils.JwtUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * LoginVO 构建工具类
 * <p>
 * 负责封装生成 LoginVO 对象的逻辑，特别是 JWT Token 的创建。
 * 设计为工具类，所有方法均为静态。
 */
public final class LoginAssembler {

    /**
     * 私有构造函数，防止该工具类被实例化。
     */
    private LoginAssembler() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 构建 LoginVO 对象。
     * <p>
     * 根据用户ID和用户名生成 AccessToken 与 RefreshToken，并封装成 LoginVO 返回。
     *
     * @param uid                 用户的唯一标识符
     * @param username            用户的用户名
     * @param jwtUtils            用于生成 JWT 的工具类实例
     * @param accessExpireMinutes AccessToken 过期时间（分钟）
     * @param refreshExpireMinutes RefreshToken 过期时间（分钟）
     * @return 包含用户ID、用户名和双 Token 的 LoginVO 对象
     */
    public static LoginVO build(String uid, String username, JwtUtils jwtUtils,
            long accessExpireMinutes, long refreshExpireMinutes) {
        // 1. AccessToken Claims
        Map<String, Object> accessClaims = new HashMap<>();
        accessClaims.put("uid", uid);
        accessClaims.put("username", username);
        accessClaims.put("tokenType", "access");

        // 2. RefreshToken Claims
        Map<String, Object> refreshClaims = new HashMap<>();
        refreshClaims.put("uid", uid);
        refreshClaims.put("username", username);
        refreshClaims.put("tokenType", "refresh");

        // 3. 调用 JwtUtils 生成双 Token
        String accessToken = jwtUtils.generateJwt(accessClaims, accessExpireMinutes);
        String refreshToken = jwtUtils.generateJwt(refreshClaims, refreshExpireMinutes);

        // 4. 返回封装好的 LoginVO 对象
        return new LoginVO(uid, username, accessToken, refreshToken);
    }
}
