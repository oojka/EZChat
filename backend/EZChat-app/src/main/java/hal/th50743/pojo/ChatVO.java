package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天室视图对象
 * <p>
 * 用于前端展示聊天室详情，包含成员列表、最后一条消息等聚合信息。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatVO {

    /**
     * 聊天室对外代码（8位数字）
     */
    private String chatCode;

    /**
     * 聊天室名称
     */
    private String chatName;

    /**
     * 所有者用户对外ID（users.uid）
     */
    private String ownerUid;

    /**
     * 聊天室头像
     */
    private Image avatar;

    /**
     * 是否允许加入（0=禁止, 1=允许）
     */
    private Integer joinEnabled;

    /**
     * 是否启用房间密码（0=未启用, 1=已启用）
     */
    private Integer passwordEnabled;

    /**
     * 成员上限（默认 200，私聊为 2）
     */
    private Integer maxMembers;

    /**
     * 聊天室类型
     * <ul>
     *   <li>0 - 群聊（Group）</li>
     *   <li>1 - 私聊（Private）</li>
     * </ul>
     */
    private Integer type;

    /**
     * 群公告
     */
    private String announcement;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveAt;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 未读消息数
     */
    private Integer unreadCount;

    /**
     * 最后一条消息
     */
    private MessageVO lastMessage;
    
    /**
     * 在线成员数
     */
    private Integer onLineMemberCount;
    
    /**
     * 总成员数
     */
    private Integer memberCount;

    /**
     * 聊天室成员列表
     */
    private List<ChatMemberVO> chatMembers;

    /**
     * 临时字段：头像对象名（用于 JOIN 查询构建 Image 对象）
     */
    private transient String avatarAssetName;
}
