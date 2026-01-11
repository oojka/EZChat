package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 访客注册请求对象（已废弃，使用 GuestJoinReq 替代）
 * <p>
 * 历史遗留接口，用于通过密码方式创建访客并加入聊天室。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GuestRegisterReq {

    /**
     * 聊天室代码（8位数字）
     */
    private String chatCode;

    /**
     * 聊天室密码
     */
    private String password;

    /**
     * 访客昵称
     */
    private String nickname;

    /**
     * 访客头像
     */
    private Image avatar;

}
