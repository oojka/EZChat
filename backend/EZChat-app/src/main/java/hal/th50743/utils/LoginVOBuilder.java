package hal.th50743.utils;

import hal.th50743.pojo.LoginVO;

import java.util.HashMap;
import java.util.Map;

/**
 * LoginVO 构建工具类
 * <p>
 * 负责封装生成 LoginVO 对象的逻辑，特别是 JWT Token 的创建。
 * 设计为工具类，所有方法均为静态。
 */
public final class LoginVOBuilder {

    /**
     * 私有构造函数，防止该工具类被实例化。
     */
    private LoginVOBuilder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 构建 LoginVO 对象。
     * <p>
     * 根据用户ID和用户名生成 JWT Token，并封装成 LoginVO 返回。
     *
     * @param uId      用户的唯一标识符
     * @param username 用户的用户名
     * @param jwtUtils 用于生成 JWT 的工具类实例
     * @return 包含用户ID、用户名和JWT Token的 LoginVO 对象
     */
    public static LoginVO build(String uId, String username, JwtUtils jwtUtils) {
        // 1. 创建 Claims，用于生成 JWT Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", uId);
        claims.put("username", username);

        // 2. 调用 JwtUtils 生成 JWT Token
        String token = jwtUtils.generateJwt(claims);

        // 3. 返回封装好的 LoginVO 对象
        return new LoginVO(uId, username, token);
    }
}
