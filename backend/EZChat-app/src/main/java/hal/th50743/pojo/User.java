package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * <p>
 * 对应数据库中的 users 表，表示系统中的用户信息。
 * 支持访客用户和正式用户两种类型。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {

    /**
     * 用户内部ID（主键，自增）
     */
    private Integer id;

    /**
     * 用户唯一标识（对外展示，10位随机字符串）
     */
    private String uid;

    /**
     * 用户昵称
     */
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

    /**
     * 用户简介
     */
    private String bio;

    /**
     * 用户最后活跃时间
     */
    private LocalDateTime lastSeenAt;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记：0=未删除，1=已删除
     */
    private Integer isDeleted;

    /**
     * 用户类型：0=访客，1=正式用户
     */
    private Integer userType;

    /**
     * 临时字段：用户名（正式用户专用，通过 JOIN 查询获取）
     */
    private String username;

    /**
     * 临时字段：用于存储 JOIN 查询的 object_name（不持久化）
     */
    private transient String avatarAssetName;

}
