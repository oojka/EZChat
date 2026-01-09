package hal.th50743.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Token 缓存配置
 * <p>
 * 使用 Caffeine 缓存 AccessToken 与访客 RefreshToken。
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
