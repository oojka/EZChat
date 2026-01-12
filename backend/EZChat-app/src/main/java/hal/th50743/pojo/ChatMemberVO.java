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

    /**
     * 成员用户对外ID（users.uid）
     */
    private String uid;

    /**
     * 所属聊天室代码
     */
    private String chatCode;

    /**
     * 成员昵称
     */
    private String nickname;

    /**
     * 成员头像
     */
    private Image avatar;

    /**
     * 在线状态（true=在线, false=离线）
     */
    private boolean online;

    /**
     * 最后在线时间
     */
    private LocalDateTime lastSeenAt;

}
