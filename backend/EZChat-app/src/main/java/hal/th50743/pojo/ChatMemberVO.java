package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天室成员视图对象
 * <p>
 * 封装发送给前端的成员信息，包含在线状态。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMemberVO {

    private String uId;
    private String nickname;
    private Image avatar;
    private boolean online;
    private LocalDateTime lastSeenAt;

}
