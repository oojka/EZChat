package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功返回对象
 * <p>
 * 包含用户认证成功后的基本信息及JWT Token。
 * 用于前端存储登录状态和后续API请求认证。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginVO {

    /**
     * 用户对外ID（users.uid）
     */
    private String uid;

    /**
     * 用户名（正式用户）或昵称（访客用户）
     */
    private String username;

    /**
     * AccessToken（用于API请求认证）
     */
    private String accessToken;

    /**
     * RefreshToken（用于刷新 AccessToken）
     */
    private String refreshToken;

}
