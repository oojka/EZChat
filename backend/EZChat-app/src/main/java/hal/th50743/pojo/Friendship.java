package hal.th50743.pojo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 好友关系实体
 * <p>
 * 对应数据库中的 friendships 表。
 * 好友关系是双向存储的：A 与 B 成为好友时，会同时存储两条记录。
 */
@Data
public class Friendship {
    /**
     * 关系ID（主键，自增）
     */
    private Integer id;

    /**
     * 用户ID（当前用户）
     */
    private Integer userId;

    /**
     * 好友ID
     */
    private Integer friendId;

    /**
     * 备注名（仅对当前用户可见）
     */
    private String alias;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
