package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 创建聊天室写入对象
 * <p>
 * 对应 chats 表写入字段。
 * <p>
 * 说明：
 * <ul>
 *   <li>Chat.java 当前不包含 chat_password_hash 字段，因此单独定义写入对象</li>
 *   <li>本工程不使用物理外键，ownerId 关联关系由业务逻辑保证</li>
 * </ul>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatCreate {

    /**
     * 聊天室ID（主键，自增）
     */
    private Integer id;

    /**
     * 聊天室对外代码（8位数字）
     */
    private String chatCode;

    /**
     * 聊天室名称
     */
    private String chatName;

    /**
     * 所有者用户内部ID（users.id）
     */
    private Integer ownerId;

    /**
     * 聊天室密码哈希（BCrypt，null 表示无密码）
     */
    private String chatPasswordHash;

    /**
     * 是否允许加入（0=禁止, 1=允许）
     */
    private Integer joinEnabled;

    /**
     * 成员上限（默认 200，私聊为 2）
     */
    private Integer maxMembers;

    /**
     * 群公告
     */
    private String announcement;

    /**
     * 头像对象ID（逻辑外键，关联 assets.id）
     */
    private Integer objectId;
    
    /**
     * 聊天室类型
     * <ul>
     *   <li>0 - 群聊（Group，默认）</li>
     *   <li>1 - 私聊（Private）</li>
     * </ul>
     */
    private Integer type;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
