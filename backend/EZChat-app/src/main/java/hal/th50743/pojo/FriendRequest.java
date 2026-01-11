package hal.th50743.pojo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 好友申请实体
 * <p>
 * 对应数据库中的 friend_requests 表。
 * 记录用户之间的好友申请信息及处理状态。
 */
@Data
public class FriendRequest {
    /**
     * 申请ID（主键，自增）
     */
    private Integer id;

    /**
     * 发送者用户ID
     */
    private Integer senderId;

    /**
     * 接收者用户ID
     */
    private Integer receiverId;
    
    /**
     * 申请状态：0=待处理(Pending)，1=已同意(Accepted)，2=已拒绝(Rejected)
     */
    private Integer status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
