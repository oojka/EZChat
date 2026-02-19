package hal.th50743.mapper;

import hal.th50743.pojo.Asset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资产数据访问层
 *
 * <p>负责文件/图片资产（assets 表）的生命周期管理和 GC 相关的数据库操作。
 *
 * <h3>主要功能</h3>
 * <ul>
 *   <li>资产 CRUD 操作（创建、查询、更新状态、删除）</li>
 *   <li>去重查询（原始哈希、规范化哈希）</li>
 *   <li>批量状态更新（消息图片激活）</li>
 *   <li>GC 清理查询（分页获取 PENDING 文件）</li>
 * </ul>
 *
 * <h3>资产状态说明</h3>
 * <ul>
 *   <li>status=0: PENDING（待激活，可被 GC 清理）</li>
 *   <li>status=1: ACTIVE（已激活，正常使用中）</li>
 * </ul>
 *
 * <h3>去重策略</h3>
 * <ul>
 *   <li>raw_asset_hash: 原始文件 SHA-256，前端预比对</li>
 *   <li>normalized_asset_hash: 规范化后 SHA-256，后端最终去重</li>
 * </ul>
 *
 * <h3>表依赖</h3>
 * <ul>
 *   <li>{@code assets} - 资产主表</li>
 * </ul>
 *
 * @see Asset
 */
@Mapper
public interface AssetMapper {

    /**
     * 插入资产记录
     *
     * <p>初始状态通常为 0（PENDING），插入后通过 useGeneratedKeys 回填 id。
     *
     * @param file 资产实体（包含 assetName、category、status 等）
     * @return 影响行数
     */
    int insertAsset(Asset file);

    /**
     * 根据 ID 查询资产记录
     *
     * <p>使用主键索引，效率最高。
     *
     * @param id 资产内部 ID
     * @return 资产实体，不存在返回 null
     */
    Asset selectById(Integer id);

    /**
     * 根据 assetName 查询资产记录
     *
     * <p>使用 idx_asset_name 索引。assetName 是 MinIO 中的对象名。
     *
     * @param assetName MinIO 对象名（唯一）
     * @return 资产实体，不存在返回 null
     */
    Asset selectByAssetName(String assetName);

    /**
     * 根据 messageId 查询关联的资产列表
     *
     * <p>使用 idx_message_id 索引。用于获取消息中的所有图片。
     *
     * @param messageId 消息内部 ID
     * @return 资产列表
     */
    List<Asset> selectByMessageId(Integer messageId);

    /**
     * 批量更新资产状态
     *
     * <p>用于消息图片激活：一条消息可能包含多张图片，使用批量更新避免循环调用。
     * 同时更新 status、category、messageId。
     *
     * @param assetNames assetName 列表
     * @param status     状态值（0=PENDING, 1=ACTIVE）
     * @param category   资产分类（如 MESSAGE_IMAGE）
     * @param messageId  关联的消息内部 ID（可为 null）
     * @return 影响行数
     */
    int updateStatusBatch(List<String> assetNames, Integer status, String category, Integer messageId);

    /**
     * 更新单个资产的状态和分类
     *
     * <p>用于头像/封面激活：上传后状态从 PENDING 变为 ACTIVE。
     *
     * @param assetName assetName（MinIO 对象名）
     * @param status    状态值（0=PENDING, 1=ACTIVE）
     * @param category  资产分类（可选，传入 null 则不更新）
     * @return 影响行数
     */
    int updateStatusAndCategory(String assetName, Integer status, String category);

    /**
     * 更新单个资产的状态
     *
     * <p>仅更新状态，不修改其他字段。
     *
     * @param assetName assetName（MinIO 对象名）
     * @param status    状态值（0=PENDING, 1=ACTIVE）
     * @return 影响行数
     */
    int updateStatus(String assetName, Integer status);

    /**
     * 分页查询待清理的 PENDING 资产
     *
     * <p>GC 任务使用，使用 idx_gc_cleanup 索引。
     * 条件：status=0 且 create_time < beforeTime。
     * 采用分页避免一次加载大量数据导致 OOM。
     *
     * @param beforeTime 时间阈值（查询早于此时间的记录）
     * @param limit      每批查询数量（建议 100）
     * @param offset     偏移量
     * @return 资产列表（最多 limit 条）
     */
    List<Asset> selectPendingFilesBefore(LocalDateTime beforeTime, int limit, int offset);

    /**
     * 根据 ID 删除资产记录
     *
     * <p>物理删除数据库记录，需配合 MinIO 删除使用。
     *
     * @param id 资产内部 ID
     * @return 影响行数
     */
    int deleteById(Integer id);

    /**
     * 批量删除资产记录
     *
     * <p>GC 批量清理时使用，提升性能。
     *
     * @param ids 资产内部 ID 列表
     * @return 影响行数
     */
    int deleteByIds(List<Integer> ids);

    /**
     * 根据原始资产哈希查询已激活的资产
     *
     * <p>使用 idx_raw_asset_hash 索引。用于前端轻量级去重比对。
     * 仅查询 status=1（ACTIVE）的记录。
     *
     * @param rawHash 原始文件 SHA-256 哈希
     * @return 资产实体，不存在返回 null
     */
    Asset selectByRawHashAndActive(String rawHash);

    /**
     * 根据规范化资产哈希查询已激活的资产
     *
     * <p>使用 idx_normalized_asset_hash 索引。用于后端最终去重。
     * 仅查询 status=1（ACTIVE）的记录。
     *
     * @param normalizedHash 规范化后文件 SHA-256 哈希
     * @return 资产实体，不存在返回 null
     */
    Asset selectByNormalizedHashAndActive(String normalizedHash);

    /**
     * 校验用户是否有权访问指定资产
     *
     * <p>访问规则：
     * <ul>
     *   <li>public 对象：任何登录用户可访问</li>
     *   <li>private 对象：仅消息所属聊天室成员可访问</li>
     * </ul>
     *
     * @param userId    当前用户 ID
     * @param assetName 对象名
     * @return true=有权限，false=无权限
     */
    boolean canUserAccessAsset(@Param("userId") Integer userId, @Param("assetName") String assetName);
}
