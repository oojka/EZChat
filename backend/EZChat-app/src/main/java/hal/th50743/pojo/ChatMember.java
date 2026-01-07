package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天室成员实体类
 * <p>
 * 映射聊天室成员关系表及关联的用户信息。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMember {

    private String uid;
    private Integer chatId;
    private String chatCode;
    private Integer userId;
    private String nickname;
    /**
     * 关联 assets 表的 id（逻辑外键）
     */
    private Integer assetId;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 临时字段：头像对象名（来自 JOIN 查询，不持久化）
     */
    private transient String avatarObjectName;

}
