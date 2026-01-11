package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件实体类
 * <p>
 * 对应数据库中的 objects 表，用于对象生命周期管理和 OSS GC。
 * 使用 FileEntity 命名避免与 java.io.File 冲突。
 *
 * 业务规则：
 * - status: 0=PENDING（待确认），1=ACTIVE（已激活）
 * - message_id: 可为 NULL（头像/封面文件不关联消息）
 * - category: 标识文件业务用途（USER_AVATAR, CHAT_COVER, MESSAGE_IMG, GENERAL）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Asset {

    /**
     * 自增主键
     */
    private Integer id;

    /**
     * MinIO 对象名（Object Key），唯一标识
     */
    private String assetName;

    /**
     * 原始文件名（用户上传时的文件名）
     */
    private String originalName;

    /**
     * 文件 MIME 类型（例如 image/jpeg, image/png）
     */
    private String contentType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件分类（USER_AVATAR, CHAT_COVER, MESSAGE_IMG, GENERAL）
     */
    private String category;

    /**
     * 关联的消息 ID（可为 NULL，头像/封面不关联消息）
     */
    private Integer messageId;

    /**
     * 文件状态：0=PENDING（待确认），1=ACTIVE（已激活）
     */
    private Integer status;

    /**
     * 原始对象哈希（SHA-256 hex），前端计算，用于第一次轻量级比对
     * <p>
     * 业务说明：
     * - 前端在上传前计算原始对象的 SHA-256 哈希
     * - 用于快速比对，避免不必要的对象上传
     * - 可能为 NULL（历史对象或前端未提供）
     */
    private String rawAssetHash;

    /**
     * 规范化对象哈希（SHA-256 hex），后端计算，用于最终去重比对
     * <p>
     * 业务说明：
     * - 后端在图片规范化后计算 SHA-256 哈希
     * - 用于最终去重比对（因为规范化后的内容才是真正存储的）
     * - 可能为 NULL（历史对象）
     */
    private String normalizedAssetHash;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
