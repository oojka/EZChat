package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {

    private Integer id;
    private String uid;
    private String nickname;
    /**
     * 关联 objects 表的 id（逻辑外键）
     * <p>
     * 业务说明：
     * - 通过 object_id 关联 objects 表获取头像对象信息
     * - 可为 NULL（用户未设置头像）
     * - 数据一致性由应用层保证（不使用物理外键约束）
     * - 如果对象被删除，应用层需要处理 object_id 的清理（设为 NULL）
     */
    private Integer assetId;
    private String bio;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 临时用户属性
    private String username;

    /**
     * 临时字段：用于存储 JOIN 查询的 object_name（不持久化）
     */
    private transient String avatarObjectName;

}
