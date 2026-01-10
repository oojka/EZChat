package hal.th50743.service.impl;

import hal.th50743.mapper.UserMapper;
import hal.th50743.service.PresenceService;
import hal.th50743.service.TokenCacheService;
import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 访客清理服务
 * <p>
 * 负责清理离线超过 2 小时的访客用户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuestCleanupService {

    private final UserMapper userMapper;
    private final TokenCacheService tokenCacheService;
    private final PresenceService presenceService;

    /**
     * 定时清理离线访客
     * <p>
     * 每 5 分钟执行一次，清理离线超过 2 小时的访客用户。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanupOfflineGuests() {
        // 修改为清理离线超过 2 小时 (120 min) 的访客
        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
        List<Integer> candidates = userMapper.selectGuestCandidatesForCleanup(cutoff);
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        Map<Integer, Session> onlineUsers = presenceService.getOnlineUsers();
        for (Integer userId : candidates) {
            if (onlineUsers.containsKey(userId)) {
                continue;
            }
            tokenCacheService.evictAccessToken(userId);
            tokenCacheService.evictGuestRefreshToken(userId);
            userMapper.updateUserDeleted(userId, 1);
            log.info("已清理离线访客: userId={}", userId);
        }
    }
}
