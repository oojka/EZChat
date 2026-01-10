package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 创建聊天室写入对象（对应 chats 表写入字段）
 *
 * 说明：
 * - Chat.java 当前不包含 chat_password_hash 字段，因此单独定义写入对象避免破坏现有模型
 * - 本工程不使用物理外键，ownerId 关联关系由业务逻辑保证
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatCreate {
    private Integer id;
    private String chatCode;
    private String chatName;
    private Integer ownerId;
    private String chatPasswordHash;
    private Integer joinEnabled;
    private Integer maxMembers;
    private String announcement;
    /**
     * 关联 objects 表的 id（头像对象，逻辑外键）
     * <p>
     * 业务说明：前端上传头像时已获得 objectId，直接传递即可，无需后端查表
     */
    private Integer objectId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

