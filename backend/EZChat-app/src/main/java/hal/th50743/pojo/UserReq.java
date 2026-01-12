package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户资料更新请求对象
 * <p>
 * 用于接收用户更新个人资料的请求参数。
 * 对应 PUT /user/profile 接口。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserReq {

    /**
     * 用户内部ID（后端从 Token 获取，前端无需传递）
     */
    private Integer userId;

    /**
     * 用户对外ID（后端从 Token 获取，前端无需传递）
     */
    private String uid;

    /**
     * 新昵称（可选，1-20 字符）
     */
    private String nickname;

    /**
     * 新头像（可选）
     */
    private Image avatar;

    /**
     * 新个人简介（可选，最多 200 字符）
     */
    private String bio;

}
