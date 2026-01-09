package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 群主转让广播视图对象
 * <p>
 * 用于 WebSocket 广播群主变更事件。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OwnerTransferBroadcastVO {

    /** 聊天室代码 */
    private String chatCode;

    /** 原群主 UID */
    private String oldOwnerUid;

    /** 新群主 UID */
    private String newOwnerUid;

    /** 新群主昵称 */
    private String newOwnerNickname;

    /** 转让时间 */
    private LocalDateTime transferredAt;
}
