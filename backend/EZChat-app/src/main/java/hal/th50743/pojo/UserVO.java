package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户视图对象
 * <p>
 * 用于向前端展示用户信息。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserVO {

    private String uid;
    private String nickname;
    private Image avatar;
    private String bio;

}
