package hal.th50743.mapper;

import hal.th50743.pojo.Asset;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件 Mapper 接口
 * <p>
 * 负责文件生命周期管理和 GC 相关的数据库操作。
 */
@Mapper
public interface AssetMapper {

    /**
     * 插入文件记录
     *
     * @param file 文件实体
     * @return 影响行数
     */
    int insertAsset(Asset file);

    /**
     * 根据 ID 查询文件记录
     *
     * @param id 文件 ID
     * @return 文件实体，不存在返回 null
     */
    Asset selectById(Integer id);

    /**
     * 根据 objectName 查询文件记录
     *
     * @param objectName MinIO 对象名
     * @return 文件实体，不存在返回 null
     */
    Asset selectByObjectName(String objectName);

    /**
     * 根据 messageId 查询关联的文件列表
     *
     * @param messageId 消息 ID
     * @return 文件列表
     */
    List<Asset> selectByMessageId(Integer messageId);

    /**
     * 批量更新文件状态（用于消息图片激活）
     * <p>
     * 业务目的：一条消息可能包含多张图片，使用批量更新避免循环调用，提升性能。
     *
     * @param objectNames objectName 列表
     * @param status      状态值（0=PENDING, 1=ACTIVE）
     * @param category    文件分类
     * @param messageId   关联的消息 ID（可为 null）
     * @return 影响行数
     */
    int updateStatusBatch(List<String> objectNames, Integer status, String category, Integer messageId);

    /**
     * 更新单个文件的状态和分类（用于头像/封面激活）
     *
     * @param objectName objectName
     * @param status     状态值（0=PENDING, 1=ACTIVE）
     * @param category   文件分类（可选，传入 null 则不更新 category）
     * @return 影响行数
     */
    int updateStatusAndCategory(String objectName, Integer status, String category);

    /**
     * 更新单个文件的状态
     *
     * @param objectName objectName
     * @param status     状态值（0=PENDING, 1=ACTIVE）
     * @return 影响行数
     */
    int updateStatus(String objectName, Integer status);

    /**
     * 分页查询待清理的 PENDING 文件（用于 GC）
     * <p>
     * 业务目的：防止一次性加载大量数据导致 OOM，使用分页查询 + do...while 循环分批处理。
     *
     * @param beforeTime 时间阈值（查询 create_time < beforeTime 的记录）
     * @param limit      每批查询数量（建议 100）
     * @param offset     偏移量
     * @return 文件列表（最多 limit 条）
     */
    List<Asset> selectPendingFilesBefore(LocalDateTime beforeTime, int limit, int offset);

    /**
     * 根据 ID 删除文件记录
     *
     * @param id 文件 ID
     * @return 影响行数
     */
    int deleteById(Integer id);

    /**
     * 批量删除文件记录（可选，用于提升 GC 性能）
     *
     * @param ids 文件 ID 列表
     * @return 影响行数
     */
    int deleteByIds(List<Integer> ids);

    /**
     * 根据原始对象哈希查询已激活的对象（用于前端轻量级比对）
     *
     * @param rawHash 原始对象哈希（SHA-256 hex）
     * @return 对象实体，不存在返回 null
     */
    Asset selectByRawHashAndActive(String rawHash);

    /**
     * 根据规范化对象哈希查询已激活的对象（用于后端最终去重）
     *
     * @param normalizedHash 规范化对象哈希（SHA-256 hex）
     * @return 对象实体，不存在返回 null
     */
    Asset selectByNormalizedHashAndActive(String normalizedHash);
}
