package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 邀请码实体（对应 chat_invites）
 *
 * 说明：
 * - 本工程不使用物理外键（FK），chat_code 关联关系由业务逻辑保证
 * - code_hash 为邀请码短码的 SHA-256 Hex，用于防止 DB 泄露直接可用
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatInvite {
    private Integer id;
    private String chatCode;
    private String codeHash;
    private LocalDateTime expiresAt;
    private Integer maxUses;
    private Integer usedCount;
    private Integer revoked;
    private Integer createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}


