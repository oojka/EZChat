package hal.th50743.service;

import hal.th50743.pojo.Image;
import org.springframework.web.multipart.MultipartFile;

/**
 * 对象存储媒体服务（统一上传/处理/删除入口）
 * <p>
 * 业务目的：
 * - 将“上传 + 图片处理 + 删除对象”等与 MinIO 强相关的逻辑集中管理，避免散落在各业务 Service 中
 * - 便于未来扩展“文件功能”（非图片文件上传/下载/删除）而不污染业务层
 * 约定：
 * - 图片会尽可能被规范化为 JPEG（旋转修正/去元数据/限制尺寸/质量压缩）
 * - 删除对象时同时尝试删除缩略图（由底层 MinIO Operator 处理）
 */
public interface OssMediaService {

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
     * 通用上传：未来“文件功能”可直接复用
     * <p>
     * - 图片会做规范化与缩略图策略
     * - 非图片会原样上传（不做格式转换）
     *
     * @param file     上传文件
     * @param isPublic true=公开访问，false=私有预签名访问
     * @return 存储对象信息
     */
    StoredObject uploadFile(MultipartFile file, boolean isPublic);

    /**
     * 删除对象（支持 objectName 或完整 URL）
     *
     * @param objectNameOrUrl 对象名或 URL
     */
    void deleteObject(String objectNameOrUrl);

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


