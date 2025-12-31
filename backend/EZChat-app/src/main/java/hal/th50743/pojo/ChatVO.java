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

    private String chatCode;
    private String chatName;
    private String ownerUId;
    private Image avatar;
    private Integer joinEnabled;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createTime;
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

    // 临时属性，用于数据转换
    private String avatarName;
}
