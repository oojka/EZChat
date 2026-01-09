package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 成员退群广播视图对象
 * <p>
 * 用于 WebSocket 广播成员退群事件。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MemberLeaveBroadcastVO {

    /** 聊天室代码 */
    private String chatCode;

    /** 退群成员 UID */
    private String uid;

    /** 退群成员显示名 */
    private String nickname;

    /** 退群时间 */
    private LocalDateTime leftAt;
}
