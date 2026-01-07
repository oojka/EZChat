package hal.th50743.service;

import hal.th50743.pojo.AssetCategory;
import hal.th50743.pojo.Asset;
import hal.th50743.pojo.Image;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 对象存储服务（统一上传/处理/删除/生命周期管理入口）
 * <p>
 * 业务目的：
 * - 将"上传 + 图片处理 + 删除对象"等与 MinIO 强相关的逻辑集中管理
 * - 将"保存记录、激活、GC 查询"等数据库生命周期操作统一管理
 * - 便于未来扩展"文件功能"（非图片文件上传/下载/删除）而不污染业务层
 * <p>
 * 约定：
 * - 图片会尽可能被规范化为 JPEG（旋转修正/去元数据/限制尺寸/质量压缩）
 * - 删除对象时同时尝试删除缩略图（由底层 MinIO Operator 处理）
 */
public interface AssetService {

    // ==================== 上传相关 ====================

    /**
     * 上传用户头像（public）
     *
     * @param file 上传文件
     * @return 图片对象（包含 objectName/url/thumbUrl）
     */
    Image uploadAvatar(MultipartFile file);

    /**
     * 上传消息图片（private）
     *
     * @param file 上传文件
     * @return 图片对象（包含 objectName/url/thumbUrl）
     */
    Image uploadMessageImage(MultipartFile file);

    /**
     * 通用上传：未来"文件功能"可直接复用
     * <p>
     * - 图片会做规范化与缩略图策略
     * - 非图片会原样上传（不做格式转换）
     *
     * @param file     上传文件
     * @param isPublic true=公开访问，false=私有预签名访问
     * @return 存储对象信息
     */
    StoredObject uploadFile(MultipartFile file, boolean isPublic);

    // ==================== 删除相关 ====================

    /**
     * 删除对象（支持 objectName 或完整 URL）
     *
     * @param objectNameOrUrl 对象名或 URL
     */
    void deleteObject(String objectNameOrUrl);

    // ==================== URL 获取相关 ====================

    /**
     * 获取对象访问 URL（按需刷新预签名链接）
     * <p>
     * 业务目的：
     * - 前端只保存 objectName，真正展示/预览时再向后端获取最新 URL，避免预签名过期
     *
     * @param objectName MinIO 对象名（建议为原图 objectName）
     * @return 可访问 URL（public 为永久链接，private 为预签名链接）
     */
    String getImageUrl(String objectName);

    /**
     * 获取图片访问 URL（包含 objectId）
     * <p>
     * 业务目的：前端在"点开大图预览"时调用，拿到最新 URL 后再去拉取原图内容。
     * <p>
     * 优化：返回 Image 对象（包含 objectId），便于前端直接使用 objectId 进行后续操作。
     *
     * @param objectName MinIO 对象名（建议为原图 objectName）
     * @return Image 对象（包含最新的 URL 和 objectId）
     */
    Image getImageUrlWithObjectId(String objectName);

    /**
     * 检查对象是否已存在（基于原始哈希）
     * <p>
     * 业务目的：前端计算原始对象哈希后，先调用此接口比对，避免不必要的对象上传。
     * <p>
     * 查询逻辑：
     * 1. 先查询 raw_object_hash（前端计算的原始对象哈希）
     * 2. 如果不存在，再查询 normalized_object_hash（兼容性：如果前端规范化与后端一致）
     * 3. 只查询 status=1（已激活）的对象
     * <p>
     * 返回逻辑：
     * - 如果对象已存在，返回 Image 对象（包含最新的 URL，避免预签名过期）
     * - 如果不存在，返回 null（前端继续上传）
     *
     * @param rawHash 原始对象哈希（SHA-256 hex，64 字符）
     * @return Image 对象（如果存在）或 null（如果不存在）
     */
    Image checkObjectExists(String rawHash);

    // ==================== 数据库记录管理 ====================

    /**
     * 保存文件记录（默认 status=0, PENDING）
     * <p>
     * 业务目的：上传到 MinIO 后立即写入 objects 表，标记为 PENDING 状态。
     *
     * @param objectName           MinIO 对象名
     * @param originalName         原始文件名
     * @param contentType          文件 MIME 类型
     * @param fileSize             文件大小（字节）
     * @param category             文件分类
     * @param rawObjectHash        原始对象哈希（SHA-256 hex），前端计算，可为 null
     * @param normalizedObjectHash 规范化对象哈希（SHA-256 hex），后端计算，可为 null
     * @return 文件实体（包含自增 ID）
     */
    Asset saveFile(String objectName, String originalName, String contentType, Long fileSize,
                   AssetCategory category, String rawObjectHash, String normalizedObjectHash);

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
    void activateFilesBatch(List<String> objectNames, AssetCategory category, Integer messageId);

    /**
     * 激活单个文件（用于头像/封面）
     *
     * @param objectName MinIO 对象名
     * @param category   文件分类
     */
    void activateFile(String objectName, AssetCategory category);

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
    List<Asset> findPendingFilesForGC(int hoursOld, int batchSize, int offset);

    /**
     * 根据原始对象哈希查询已激活的对象（用于前端轻量级比对）
     *
     * @param rawHash 原始对象哈希（SHA-256 hex）
     * @return 对象实体，不存在返回 null
     */
    Asset findActiveObjectByRawHash(String rawHash);

    /**
     * 根据规范化对象哈希查询已激活的对象（用于后端最终去重）
     *
     * @param normalizedHash 规范化对象哈希（SHA-256 hex）
     * @return 对象实体，不存在返回 null
     */
    Asset findActiveObjectByNormalizedHash(String normalizedHash);

    /**
     * 根据 ID 查询对象实体
     *
     * @param id 对象 ID
     * @return 对象实体，不存在返回 null
     */
    Asset findById(Integer id);

    /**
     * 根据 objectName 查询对象实体
     *
     * @param objectName MinIO 对象名
     * @return 对象实体，不存在返回 null
     */
    Asset findByObjectName(String objectName);

    // ==================== DTO ====================

    /**
     * 存储对象 DTO
     *
     * @param objectName  对象名（MinIO objectName）
     * @param url         访问 URL（public 为永久链接，private 为预签名链接）
     * @param thumbUrl    缩略图 URL（若无缩略图则回退为 url）
     * @param contentType Content-Type
     */
    record StoredObject(String objectName, String url, String thumbUrl, String contentType) {
    }
}
