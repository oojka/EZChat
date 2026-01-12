package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 邀请码实体类
 * <p>
 * 对应数据库中的 chat_invites 表。
 * <p>
 * 业务说明：
 * <ul>
 *   <li>本工程不使用物理外键（FK），chat_id 关联关系由业务逻辑保证</li>
 *   <li>invite_code 用于后台管理展示与复制</li>
 *   <li>code_hash 为邀请码短码的 SHA-256 Hex，用于校验与防止明文滥用</li>
 * </ul>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatInvite {

    /**
     * 邀请码ID（主键，自增）
     */
    private Integer id;

    /**
     * 所属聊天室内部ID（chats.id）
     */
    private Integer chatId;

    /**
     * 邀请码明文（16-24位字符，用于展示和分发）
     */
    private String inviteCode;

    /**
     * 邀请码哈希（SHA-256 Hex，用于数据库查询匹配）
     */
    private String codeHash;

    /**
     * 过期时间（null 表示永不过期）
     */
    private LocalDateTime expiresAt;

    /**
     * 最大使用次数（0 或 null 表示无限制）
     */
    private Integer maxUses;

    /**
     * 已使用次数
     */
    private Integer usedCount;

    /**
     * 是否已撤销（0=有效, 1=已撤销）
     */
    private Integer revoked;

    /**
     * 创建者用户内部ID（users.id）
     */
    private Integer createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
