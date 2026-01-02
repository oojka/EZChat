package hal.th50743.service.impl;

import hal.th50743.mapper.FileMapper;
import hal.th50743.pojo.FileCategory;
import hal.th50743.pojo.FileEntity;
import hal.th50743.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件服务实现类
 * <p>
 * 负责文件生命周期管理：保存、激活、GC 查询等业务逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileMapper fileMapper;

    /**
     * 保存文件记录（默认 status=0, PENDING）
     *
     * @param objectName          MinIO 对象名
     * @param originalName        原始文件名
     * @param contentType         文件 MIME 类型
     * @param fileSize            文件大小（字节）
     * @param category            文件分类
     * @param rawObjectHash       原始对象哈希（SHA-256 hex），可为 null
     * @param normalizedObjectHash 规范化对象哈希（SHA-256 hex），可为 null
     * @return 文件实体（包含自增 ID）
     */
    @Override
    public FileEntity saveFile(String objectName, String originalName, String contentType, Long fileSize, FileCategory category, String rawObjectHash, String normalizedObjectHash) {
        FileEntity file = new FileEntity();
        file.setObjectName(objectName);
        file.setOriginalName(originalName);
        file.setContentType(contentType);
        file.setFileSize(fileSize);
        file.setCategory(category.getValue());
        file.setMessageId(null); // 初始状态不关联消息
        file.setStatus(0); // PENDING
        file.setRawObjectHash(rawObjectHash); // 原始对象哈希
        file.setNormalizedObjectHash(normalizedObjectHash); // 规范化对象哈希
        file.setCreateTime(LocalDateTime.now());
        file.setUpdateTime(LocalDateTime.now());

        fileMapper.insert(file);
        log.debug("Saved file record: objectName={}, category={}, status=PENDING, rawHash={}, normalizedHash={}", objectName, category, rawObjectHash, normalizedObjectHash);
        return file;
    }

    /**
     * 批量激活文件（用于消息图片）
     *
     * @param objectNames objectName 列表
     * @param category    文件分类（通常为 MESSAGE_IMG）
     * @param messageId   关联的消息 ID
     */
    @Override
    public void activateFilesBatch(List<String> objectNames, FileCategory category, Integer messageId) {
        if (objectNames == null || objectNames.isEmpty()) {
            return;
        }
        int updated = fileMapper.updateStatusBatch(objectNames, 1, category.getValue(), messageId);
        log.debug("Activated {} files batch: category={}, messageId={}", updated, category, messageId);
    }

    /**
     * 激活单个文件（用于头像/封面）
     *
     * @param objectName MinIO 对象名
     * @param category   文件分类
     */
    @Override
    public void activateFile(String objectName, FileCategory category) {
        int updated = fileMapper.updateStatusAndCategory(objectName, 1, category.getValue());
        if (updated > 0) {
            log.debug("Activated file: objectName={}, category={}", objectName, category);
        } else {
            log.warn("File not found for activation: objectName={}", objectName);
        }
    }

    /**
     * 激活头像文件（便捷方法）
     *
     * @param objectName MinIO 对象名
     */
    @Override
    public void activateAvatarFile(String objectName) {
        activateFile(objectName, FileCategory.USER_AVATAR);
    }

    /**
     * 激活群头像文件（便捷方法）
     *
     * @param objectName MinIO 对象名
     */
    @Override
    public void activateChatCoverFile(String objectName) {
        activateFile(objectName, FileCategory.CHAT_COVER);
    }

    /**
     * 分页查询待清理文件（用于 GC）
     *
     * @param hoursOld  文件年龄（小时）
     * @param batchSize 每批查询数量
     * @param offset    偏移量
     * @return 文件列表
     */
    @Override
    public List<FileEntity> findPendingFilesForGC(int hoursOld, int batchSize, int offset) {
        LocalDateTime beforeTime = LocalDateTime.now().minusHours(hoursOld);
        return fileMapper.findPendingFilesBefore(beforeTime, batchSize, offset);
    }

    /**
     * 根据原始对象哈希查询已激活的对象（用于前端轻量级比对）
     *
     * @param rawHash 原始对象哈希（SHA-256 hex）
     * @return 对象实体，不存在返回 null
     */
    @Override
    public FileEntity findActiveObjectByRawHash(String rawHash) {
        return fileMapper.findByRawHashAndActive(rawHash);
    }

    /**
     * 根据规范化对象哈希查询已激活的对象（用于后端最终去重）
     *
     * @param normalizedHash 规范化对象哈希（SHA-256 hex）
     * @return 对象实体，不存在返回 null
     */
    @Override
    public FileEntity findActiveObjectByNormalizedHash(String normalizedHash) {
        return fileMapper.findByNormalizedHashAndActive(normalizedHash);
    }

    /**
     * 根据 ID 查询对象实体
     *
     * @param id 对象 ID
     * @return 对象实体，不存在返回 null
     */
    @Override
    public FileEntity findById(Integer id) {
        return fileMapper.findById(id);
    }

    /**
     * 根据 objectName 查询对象实体
     *
     * @param objectName MinIO 对象名
     * @return 对象实体，不存在返回 null
     */
    @Override
    public FileEntity findByObjectName(String objectName) {
        return fileMapper.findByObjectName(objectName);
    }
}

