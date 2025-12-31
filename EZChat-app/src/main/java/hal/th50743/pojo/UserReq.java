package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户请求对象
 * <p>
 * 用于接收用户更新个人资料的请求参数。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserReq {

    private Integer userId;
    private String uId;
    private String nickname;
    private Image avatar;
    private String bio;

}
