package hal.th50743.pojo.vo;

import hal.th50743.pojo.Image;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 好友视图对象
 * <p>
 * 用于前端展示好友信息，包含好友资料、在线状态及备注等。
 */
@Data
public class FriendVO {

    /**
     * 好友用户内部ID（users.id）
     */
    private Integer userId;

    /**
     * 好友用户对外ID（users.uid）
     */
    private String uid;

    /**
     * 好友昵称
     */
    private String nickname;

    /**
     * 好友备注名（当前用户对该好友设置的备注）
     */
    private String alias;

    /**
     * 好友头像
     */
    private Image avatar;

    /**
     * 好友个人简介
     */
    private String bio;

    /**
     * 在线状态（true=在线, false=离线）
     */
    private Boolean online;

    /**
     * 最后在线时间
     */
    private LocalDateTime lastSeenAt;

    /**
     * 好友关系建立时间
     */
    private LocalDateTime createTime;
}
