package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邀请链接创建请求对象
 * <p>
 * 用于房间设置中生成新的邀请链接。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatInviteCreateReq {

    /**
     * 邀请链接过期时间（分钟，可选）
     * <p>
     * 业务规则：
     * - 为空：使用默认 7 天（10080 分钟）
     * - 传值：必须为正数
     */
    private Integer joinLinkExpiryMinutes;

    /**
     * 邀请链接最大使用次数（可选）
     * <p>
     * 业务规则：
     * - null 或 0：无限使用
     * - 1：一次性链接
     */
    private Integer maxUses;
}
