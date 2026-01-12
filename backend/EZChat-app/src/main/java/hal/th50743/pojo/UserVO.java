package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户视图对象
 * <p>
 * 用于向前端展示用户基本信息。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserVO {

    /**
     * 用户对外ID（10位随机字符串）
     */
    private String uid;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 用户头像
     */
    private Image avatar;

    /**
     * 个人简介
     */
    private String bio;

}
