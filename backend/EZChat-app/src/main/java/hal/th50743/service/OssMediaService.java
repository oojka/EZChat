package hal.th50743.service;

import hal.th50743.pojo.Image;
import org.springframework.web.multipart.MultipartFile;

/**
 * 对象存储媒体服务（统一上传/处理/删除入口）
 * <p>
 * 业务目的：
 * - 将“上传 + 图片处理 + 删除对象”等与 MinIO 强相关的逻辑集中管理，避免散落在各业务 Service 中
 * - 便于未来扩展“文件功能”（非图片文件上传/下载/删除）而不污染业务层
 *
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


