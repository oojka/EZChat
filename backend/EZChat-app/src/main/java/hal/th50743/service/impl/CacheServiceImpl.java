package hal.th50743.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import hal.th50743.pojo.ChatVO;
import hal.th50743.pojo.MessageVO;
import hal.th50743.service.AuthService;
import hal.th50743.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Token 缓存服务实现类 - JWT Token 缓存管理
 *
 * <h3>职责概述</h3>
 * <p>
 * 统一管理 AccessToken 和访客 RefreshToken 的内存缓存操作。
 * 使用 Caffeine 高性能缓存库，支持自动过期和容量限制。
 * </p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li><b>AccessToken 缓存</b>：所有用户的 AccessToken（内存存储）</li>
 *     <li><b>访客 RefreshToken 缓存</b>：访客的 RefreshToken（正式用户存 DB）</li>
 * </ul>
 *
 * <h3>调用路径</h3>
 * <ul>
 *     <li>{@code AuthService} → 本服务：Token 签发和刷新时缓存</li>
 *     <li>{@code WebSocketServer} → 本服务：连接时验证 AccessToken</li>
 *     <li>{@code JwtAuthInterceptor} → 本服务：API 请求时验证 AccessToken</li>
 * </ul>
 *
 * <h3>设计考量</h3>
 * <ul>
 *     <li>AccessToken 存缓存而非 DB，降低验证延迟</li>
 *     <li>访客 RefreshToken 存缓存，服务重启后失效（符合访客临时性）</li>
 *     <li>正式用户 RefreshToken 存 DB，支持持久化和跨服务校验</li>
 * </ul>
 *
 * @author 系统开发者
 * @since 1.0
 * @see AuthService
 * @see com.github.benmanes.caffeine.cache.Cache
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheServiceImpl implements CacheService {

    private final Cache<Integer, String> accessTokenCache;
    private final Cache<Integer, String> guestRefreshTokenCache;
//    //
//    private final Cache<Integer, ChatVO> chatVOReadCache;
//    //
//    private final Cache<Integer, MessageVO> messageVOReadCache;

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
