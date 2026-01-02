package hal.th50743.service;

import hal.th50743.pojo.FileCategory;
import hal.th50743.pojo.FileEntity;

import java.util.List;

/**
 * 文件服务接口
 * <p>
 * 负责文件生命周期管理：保存、激活、GC 查询等业务逻辑。
 */
public interface FileService {

    /**
     * 保存文件记录（默认 status=0, PENDING）
     * <p>
     * 业务目的：上传到 MinIO 后立即写入 objects 表，标记为 PENDING 状态。
     *
     * @param objectName          MinIO 对象名
     * @param originalName        原始文件名
     * @param contentType         文件 MIME 类型
     * @param fileSize            文件大小（字节）
     * @param category            文件分类
     * @param rawObjectHash       原始对象哈希（SHA-256 hex），前端计算，可为 null
     * @param normalizedObjectHash 规范化对象哈希（SHA-256 hex），后端计算，可为 null
     * @return 文件实体（包含自增 ID）
     */
    FileEntity saveFile(String objectName, String originalName, String contentType, Long fileSize, FileCategory category, String rawObjectHash, String normalizedObjectHash);

    /**
     * 批量激活文件（用于消息图片）
     * <p>
     * 业务目的：消息发送成功后，批量激活所有关联的图片文件。
     * 性能优化：一次 SQL 完成所有图片的状态更新，避免循环调用。
     *
     * @param objectNames objectName 列表
     * @param category    文件分类（通常为 MESSAGE_IMG）
     * @param messageId   关联的消息 ID
     */
    void activateFilesBatch(List<String> objectNames, FileCategory category, Integer messageId);

    /**
     * 激活单个文件（用于头像/封面）
     *
     * @param objectName MinIO 对象名
     * @param category   文件分类
     */
    void activateFile(String objectName, FileCategory category);

    /**
     * 激活头像文件（便捷方法）
     *
     * @param objectName MinIO 对象名
     */
    void activateAvatarFile(String objectName);

    /**
     * 激活群头像文件（便捷方法）
     *
     * @param objectName MinIO 对象名
     */
    void activateChatCoverFile(String objectName);

    /**
     * 分页查询待清理文件（用于 GC）
     * <p>
     * 业务目的：防止一次性加载大量数据导致 OOM，使用分页查询。
     *
     * @param hoursOld  文件年龄（小时），查询 create_time < (NOW - hoursOld) 的记录
     * @param batchSize 每批查询数量（建议 100）
     * @param offset    偏移量
     * @return 文件列表（最多 batchSize 条）
     */
    List<FileEntity> findPendingFilesForGC(int hoursOld, int batchSize, int offset);

    /**
     * 根据原始对象哈希查询已激活的对象（用于前端轻量级比对）
     *
     * @param rawHash 原始对象哈希（SHA-256 hex）
     * @return 对象实体，不存在返回 null
     */
    FileEntity findActiveObjectByRawHash(String rawHash);

    /**
     * 根据规范化对象哈希查询已激活的对象（用于后端最终去重）
     *
     * @param normalizedHash 规范化对象哈希（SHA-256 hex）
     * @return 对象实体，不存在返回 null
     */
    FileEntity findActiveObjectByNormalizedHash(String normalizedHash);

    /**
     * 根据 ID 查询对象实体
     *
     * @param id 对象 ID
     * @return 对象实体，不存在返回 null
     */
    FileEntity findById(Integer id);

    /**
     * 根据 objectName 查询对象实体
     *
     * @param objectName MinIO 对象名
     * @return 对象实体，不存在返回 null
     */
    FileEntity findByObjectName(String objectName);
}


