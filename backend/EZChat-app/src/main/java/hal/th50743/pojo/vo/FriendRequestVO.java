package hal.th50743.pojo.vo;

import hal.th50743.pojo.Image;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 好友申请视图对象
 * <p>
 * 用于前端展示收到的好友申请信息。
 */
@Data
public class FriendRequestVO {

    /**
     * 申请记录ID（friend_requests.id）
     */
    private Integer id;

    /**
     * 发送者用户内部ID（users.id）
     */
    private Integer senderId;

    /**
     * 发送者用户对外ID（users.uid）
     */
    private String senderUid;

    /**
     * 发送者昵称
     */
    private String senderNickname;

    /**
     * 发送者头像
     */
    private Image senderAvatar;

    /**
     * 申请状态
     * <p>
     * 业务规则：
     * - 0: Pending（待处理）
     * - 1: Accepted（已同意）
     * - 2: Rejected（已拒绝）
     */
    private Integer status;

    /**
     * 申请创建时间
     */
    private LocalDateTime createTime;
}
