package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天室成员实体类
 * <p>
 * 映射 chat_members 表及关联的用户信息。
 * 表示用户与聊天室的成员关系。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMember {

    /**
     * 成员用户对外ID（users.uid）
     */
    private String uid;

    /**
     * 所属聊天室内部ID（chats.id）
     */
    private Integer chatId;

    /**
     * 所属聊天室对外代码（chats.chat_code）
     */
    private String chatCode;

    /**
     * 成员用户内部ID（users.id）
     */
    private Integer userId;

    /**
     * 成员昵称（来自 users.nickname）
     */
    private String nickname;

    /**
     * 头像对象ID（逻辑外键，关联 assets.id）
     */
    private Integer assetId;

    /**
     * 最后阅读时间（用于计算未读消息数）
     */
    private LocalDateTime lastSeenAt;

    /**
     * 加入时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 临时字段：头像对象名（来自 JOIN 查询，不持久化）
     */
    private transient String avatarAssetName;

}
