package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户在线状态对象
 * <p>
 * 用于 WebSocket 广播用户上线/离线状态变更。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserStatus {

    /**
     * 用户对外ID（users.uid）
     */
    private String uid;

    /**
     * 在线状态（true=在线, false=离线）
     */
    private boolean online;

    /**
     * 状态更新时间
     */
    private LocalDateTime updateTime;

}
