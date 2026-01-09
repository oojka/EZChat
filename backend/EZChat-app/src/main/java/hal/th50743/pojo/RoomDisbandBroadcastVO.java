package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 群聊解散广播视图对象
 * <p>
 * 用于 WebSocket 广播群聊解散事件。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomDisbandBroadcastVO {

    /** 聊天室代码 */
    private String chatCode;

    /** 操作人 UID */
    private String operatorUid;

    /** 操作人昵称 */
    private String operatorNickname;

    /** 解散时间 */
    private LocalDateTime disbandAt;
}
