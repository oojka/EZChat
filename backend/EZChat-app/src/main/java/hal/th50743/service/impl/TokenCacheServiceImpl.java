package hal.th50743.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import hal.th50743.service.TokenCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Token 缓存服务实现
 * <p>
 * 统一管理 AccessToken 与访客 RefreshToken 的缓存操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCacheServiceImpl implements TokenCacheService {

    private final Cache<Integer, String> accessTokenCache;
    private final Cache<Integer, String> guestRefreshTokenCache;

    /**
     * 缓存 AccessToken
     *
     * @param userId 用户ID
     * @param token  AccessToken
     */
    @Override
    public void cacheAccessToken(Integer userId, String token) {
        if (userId == null || token == null) {
            log.warn("Failed to cache AccessToken: userId or token is null");
            return;
        }
        accessTokenCache.put(userId, token);
    }

    /**
     * 获取 AccessToken
     *
     * @param userId 用户ID
     * @return AccessToken
     */
    @Override
    public String getAccessToken(Integer userId) {
        if (userId == null) {
            return null;
        }
        return accessTokenCache.getIfPresent(userId);
    }

    /**
     * 移除 AccessToken
     *
     * @param userId 用户ID
     */
    @Override
    public void evictAccessToken(Integer userId) {
        if (userId == null) {
            return;
        }
        accessTokenCache.invalidate(userId);
    }

    /**
     * 缓存访客 RefreshToken
     *
     * @param userId 用户ID
     * @param token  RefreshToken
     */
    @Override
    public void cacheGuestRefreshToken(Integer userId, String token) {
        if (userId == null || token == null) {
            log.warn("Failed to cache Guest RefreshToken: userId or token is null");
            return;
        }
        guestRefreshTokenCache.put(userId, token);
    }

    /**
     * 获取访客 RefreshToken
     *
     * @param userId 用户ID
     * @return RefreshToken
     */
    @Override
    public String getGuestRefreshToken(Integer userId) {
        if (userId == null) {
            return null;
        }
        return guestRefreshTokenCache.getIfPresent(userId);
    }

    /**
     * 移除访客 RefreshToken
     *
     * @param userId 用户ID
     */
    @Override
    public void evictGuestRefreshToken(Integer userId) {
        if (userId == null) {
            return;
        }
        guestRefreshTokenCache.invalidate(userId);
    }
}
