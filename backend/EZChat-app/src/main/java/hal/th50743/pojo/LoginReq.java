package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求对象
 * <p>
 * 用于接收正式用户登录的请求参数。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginReq {

    /**
     * 用户名（登录账号）
     */
    private String username;

    /**
     * 密码
     */
    private String password;

}
