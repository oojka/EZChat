package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天室密码更新请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatPasswordUpdateReq {

    /**
     * 密码保护开关
     * <p>
     * 1=开启，0=关闭
     */
    private Integer joinEnableByPassword;

    /**
     * 新密码（开启时必填）
     */
    private String password;

    /**
     * 确认密码（开启时必填）
     */
    private String passwordConfirm;
}
