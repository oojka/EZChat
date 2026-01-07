package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 成员加入广播视图对象
 * <p>
 * 扩展标准消息对象，附带新成员信息，用于 WebSocket 广播。
 * 对应前端 Type 11 (Member Join) 消息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class JoinBroadcastVO extends MessageVO {

    /**
     * 新加入的成员信息
     * 用于前端由 WebSocket 直接更新成员列表
     */
    private ChatMemberVO member;

}
