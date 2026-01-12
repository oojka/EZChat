package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息实体类
 * <p>
 * 对应数据库中的 messages 表，存储聊天消息的核心数据。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Message {

    /**
     * 消息ID（主键，自增）
     */
    private Integer id;

    /**
     * 发送者用户内部ID（users.id）
     */
    private Integer senderId;

    /**
     * 所属聊天室内部ID（chats.id）
     */
    private Integer chatId;

    /**
     * 群内消息序号（用于分页和排序）
     * <p>
     * 业务规则：每个聊天室独立递增
     */
    private Long seqId;

    /**
     * 消息类型
     * <ul>
     *   <li>0 - 纯文本消息</li>
     *   <li>1 - 纯图片消息</li>
     *   <li>2 - 混合消息（文本+图片）</li>
     * </ul>
     */
    private Integer type;

    /**
     * 消息文本内容（纯图片消息时可为空）
     */
    private String text;

    /**
     * 图片对象ID列表（JSON 数组格式，如 [1,2,3]）
     * <p>
     * 关联 assets 表的 id
     */
    private String assetIds;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
