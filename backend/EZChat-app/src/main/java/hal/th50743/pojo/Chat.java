package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天室实体类
 * <p>
 * 对应数据库中的 chats 表，表示一个聊天室的基本信息。
 * 包含聊天室代码、名称、所有者、头像等核心信息。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Chat {

    /**
     * 聊天室内部ID（主键）
     */
    private Integer id;

    /**
     * 聊天室对外代码（8位数字，唯一标识）
     * <p>
     * 业务规则：
     * - 格式：8位数字
     * - 唯一性：全局唯一
     * - 用途：用户通过此代码加入聊天室
     */
    private String chatCode;

    /**
     * 聊天室名称
     */
    private String chatName;

    /**
     * 所有者用户内部ID（users.id）
     * <p>
     * 业务规则：
     * - 创建聊天室的用户
     * - 拥有最高权限（如删除聊天室、管理成员等）
     */
    private Integer ownerId;

    /**
     * 头像对象ID（关联 objects 表）
     * <p>
     * 业务规则：
     * - 逻辑外键，关联 objects.id
     * - 可为 null（使用默认头像）
     */
    private Integer objectId;

    /**
     * 临时字段：头像对象名（用于 JOIN 查询，不持久化）
     * <p>
     * 业务说明：
     * - 通过 LEFT JOIN objects 表获取 object_name
     * - 用于构建 Image 对象，避免多次查询
     * - 使用 transient 关键字标记，不参与序列化
     */
    private transient String avatarObjectName;

    /**
     * 是否允许加入
     * <p>
     * 业务规则：
     * - 1: 允许加入（默认）
     * - 0: 禁止加入（房间已关闭）
     */
    private Integer joinEnabled;

    /**
     * 群成员上限
     * <p>
     * 业务规则：
     * - 范围：2 ~ 200
     * - 触发加入校验，超过上限则拒绝加入
     */
    private Integer maxMembers;

    /**
     * 群公告
     * <p>
     * 业务规则：
     * - 最多 500 字符
     * - 支持换行
     */
    private String announcement;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
