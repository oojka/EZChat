package hal.th50743.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Token 缓存配置
 *
 * <p>使用 Caffeine 本地缓存存储 Token，提升认证性能并支持双 Token 机制。
 *
 * <h3>缓存类型</h3>
 * <ul>
 *   <li><b>accessTokenCache</b> - AccessToken 缓存（5分钟过期）</li>
 *   <li><b>guestRefreshTokenCache</b> - 访客 RefreshToken 缓存（1天过期）</li>
 * </ul>
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>AccessToken 短期有效，存储在内存中提升验证性能</li>
 *   <li>正式用户 RefreshToken 存储在数据库中（持久化）</li>
 *   <li>访客 RefreshToken 存储在缓存中（无需持久化，过期即失效）</li>
 * </ul>
 *
 * <h3>缓存配置</h3>
 * <ul>
 *   <li>过期策略：写入后过期（expireAfterWrite）</li>
 *   <li>最大容量：100,000 条目</li>
 *   <li>Key：用户 ID（Integer）</li>
 *   <li>Value：Token 字符串（String）</li>
 * </ul>
 *
 * @see hal.th50743.service.TokenCacheService Token 缓存服务
 * @see TokenProperties Token 过期时间配置
 */
@Configuration
public class TokenCacheConfig {

    /**
     * AccessToken 缓存（5分钟过期）
     *
     * @return AccessToken 缓存实例
     */
    @Bean
    public Cache<Integer, String> accessTokenCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(100_000)
                .build();
    }

    /**
     * 访客 RefreshToken 缓存（1天过期）
     *
     * @return 访客 RefreshToken 缓存实例
     */
    @Bean
    public Cache<Integer, String> guestRefreshTokenCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofDays(1))
                .maximumSize(100_000)
                .build();
    }
}
