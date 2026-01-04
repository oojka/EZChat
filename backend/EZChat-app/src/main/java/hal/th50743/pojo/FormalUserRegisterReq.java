package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 正式用户注册请求对象
 * <p>
 * 用于接收前端正式用户注册的请求参数。
 * 支持新用户注册和临时用户转正两种场景。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FormalUserRegisterReq {

    /**
     * 用户表主键ID（可选，用于临时用户转正场景）
     */
    private Integer userId;

    /**
     * 用户对外ID（可选，用于临时用户转正场景）
     */
    private String userUid;

    /**
     * 用户名（必填，登录账号）
     */
    private String username;

    /**
     * 密码（必填）
     */
    private String password;

    /**
     * 密码确认（可选，前端已校验）
     */
    private String confirmPassword;

    /**
     * 昵称（必填）
     */
    private String nickname;

    /**
     * 头像（可选）
     */
    private Image avatar;

}
