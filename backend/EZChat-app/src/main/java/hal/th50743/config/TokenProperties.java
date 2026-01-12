package hal.th50743.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Token 过期时间配置
 *
 * <p>从 application.yml 的 {@code jwt.token} 前缀读取 Token 过期时间配置。
 *
 * <h3>配置项</h3>
 * <table border="1">
 *   <tr><th>属性</th><th>默认值</th><th>说明</th></tr>
 *   <tr><td>accessExpireMinutes</td><td>5分钟</td><td>AccessToken 有效期</td></tr>
 *   <tr><td>formalRefreshExpireMinutes</td><td>7天</td><td>正式用户 RefreshToken 有效期</td></tr>
 *   <tr><td>guestRefreshExpireMinutes</td><td>1天</td><td>访客 RefreshToken 有效期</td></tr>
 * </table>
 *
 * <h3>配置示例</h3>
 * <pre>{@code
 * jwt:
 *   token:
 *     access-expire-minutes: 5
 *     formal-refresh-expire-minutes: 10080  # 7天
 *     guest-refresh-expire-minutes: 1440    # 1天
 * }</pre>
 *
 * <h3>双 Token 机制</h3>
 * <ul>
 *   <li><b>AccessToken</b>：短期有效，每次请求携带，过期后触发静默刷新</li>
 *   <li><b>RefreshToken</b>：长期有效，用于刷新 AccessToken</li>
 * </ul>
 *
 * @see TokenCacheConfig Token 缓存配置
 * @see hal.th50743.service.TokenCacheService Token 缓存服务
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt.token")
public class TokenProperties {

    /**
     * AccessToken 过期时间（分钟）
     *
     * <p>默认 5 分钟，过期后前端自动使用 RefreshToken 刷新。
     */
    private long accessExpireMinutes = 5;

    /**
     * 正式用户 RefreshToken 过期时间（分钟）
     *
     * <p>默认 7 天（10080 分钟），存储在数据库中。
     */
    private long formalRefreshExpireMinutes = 7 * 24 * 60;

    /**
     * 访客 RefreshToken 过期时间（分钟）
     *
     * <p>默认 1 天（1440 分钟），仅存储在内存缓存中。
     */
    private long guestRefreshExpireMinutes = 24 * 60;
}
