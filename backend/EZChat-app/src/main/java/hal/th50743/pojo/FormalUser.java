package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 正式用户实体类
 * <p>
 * 对应数据库中的 formal_users 表，表示已注册的正式用户账号信息。
 * 包含用户名、密码哈希及登录时间等认证相关信息。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FormalUser {

    /**
     * 用户表主键ID（关联 users.id）
     */
    private Integer userId;

    /**
     * 用户名（登录账号，唯一）
     */
    private String username;

    /**
     * 密码哈希（BCrypt加密）
     */
    private String passwordHash;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * RefreshToken（正式用户）
     */
    private String refreshToken;

    /**
     * 用户对外ID（通过连表查询获取 users.uid）
     */
    private String userUid;

}
