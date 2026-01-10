package hal.th50743.pojo;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 成员被移除广播视图对象
 * <p>
 * 用于 WebSocket 广播群主移除成员事件。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MemberRemovedBroadcastVO {

    /** 聊天室代码 */
    private String chatCode;

    /** 被移除成员 UID */
    private String removedUid;

    /** 被移除成员显示名 */
    private String removedNickname;

    /** 操作人 UID */
    private String operatorUid;

    /** 操作人显示名 */
    private String operatorNickname;

    /** 被移除时间 */
    private LocalDateTime removedAt;
}
