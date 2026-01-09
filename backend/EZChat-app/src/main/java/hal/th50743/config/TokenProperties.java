package hal.th50743.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Token 过期时间配置（分钟）
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt.token")
public class TokenProperties {
    /**
     * AccessToken 过期时间（分钟）
     */
    private long accessExpireMinutes = 5;

    /**
     * 正式用户 RefreshToken 过期时间（分钟）
     */
    private long formalRefreshExpireMinutes = 7 * 24 * 60;

    /**
     * 访客 RefreshToken 过期时间（分钟）
     */
    private long guestRefreshExpireMinutes = 24 * 60;
}
