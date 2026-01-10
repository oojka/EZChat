package hal.th50743.pojo;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 强制下线广播视图对象
 * <p>
 * 用于通知用户在其他端登录导致被迫下线。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ForceLogoutBroadcastVO {

    /** 用户 UID */
    private String uid;

    /** 下线原因 */
    private String reason;

    /** 强制下线时间 */
    private LocalDateTime forcedAt;
}
