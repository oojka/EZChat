package hal.th50743.service;

/**
 * Token 缓存服务接口
 * <p>
 * 负责 AccessToken 与访客 RefreshToken 的缓存管理。
 */
public interface CacheService {

    /**
     * 缓存 AccessToken
     *
     * @param userId 用户ID
     * @param token  AccessToken
     */
    void cacheAccessToken(Integer userId, String token);

    /**
     * 获取 AccessToken
     *
     * @param userId 用户ID
     * @return AccessToken
     */
    String getAccessToken(Integer userId);

    /**
     * 移除 AccessToken
     *
     * @param userId 用户ID
     */
    void evictAccessToken(Integer userId);

    /**
     * 缓存访客 RefreshToken
     *
     * @param userId 用户ID
     * @param token  RefreshToken
     */
    void cacheGuestRefreshToken(Integer userId, String token);

    /**
     * 获取访客 RefreshToken
     *
     * @param userId 用户ID
     * @return RefreshToken
     */
    String getGuestRefreshToken(Integer userId);

    /**
     * 移除访客 RefreshToken
     *
     * @param userId 用户ID
     */
    void evictGuestRefreshToken(Integer userId);
}
