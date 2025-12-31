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

    private Integer chatId;
    private String chatCode;
    private Integer userId;
    private String uId;
    private String nickname;
    
    /**
     * 数据库存储的头像对象名
     */
    private String avatarObject;
    
    /**
     * 处理后的头像对象（包含完整URL）
     */
    private Image avatar;

    private LocalDateTime lastSeenAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
