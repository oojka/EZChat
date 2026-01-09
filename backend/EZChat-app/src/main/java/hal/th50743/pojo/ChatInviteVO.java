package hal.th50743.pojo;

import java.time.LocalDateTime;

/**
 * 邀请链接视图对象
 *
 * @param id 邀请码ID
 * @param inviteCode 邀请码明文
 * @param expiresAt 过期时间
 * @param maxUses 最大使用次数（0=无限）
 * @param usedCount 已使用次数
 * @param createTime 创建时间
 */
public record ChatInviteVO(
        Integer id,
        String inviteCode,
        LocalDateTime expiresAt,
        Integer maxUses,
        Integer usedCount,
        LocalDateTime createTime
) {
}
