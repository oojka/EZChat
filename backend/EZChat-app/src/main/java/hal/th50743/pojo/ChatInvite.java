package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 邀请码实体（对应 chat_invites）
 *
 * 说明：
 * - 本工程不使用物理外键（FK），chat_id 关联关系由业务逻辑保证
 * - invite_code 用于后台管理展示与复制
 * - code_hash 为邀请码短码的 SHA-256 Hex，用于校验与防止明文滥用
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatInvite {
    private Integer id;
    private Integer chatId;
    private String inviteCode;
    private String codeHash;
    private LocalDateTime expiresAt;
    private Integer maxUses;
    private Integer usedCount;
    private Integer revoked;
    private Integer createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
