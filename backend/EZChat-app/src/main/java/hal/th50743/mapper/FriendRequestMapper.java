package hal.th50743.mapper;

import hal.th50743.pojo.FriendRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 好友申请数据访问层
 *
 * <p>负责好友申请（friend_requests 表）的数据库操作。
 *
 * <h3>主要功能</h3>
 * <ul>
 *   <li>好友申请 CRUD 操作（创建、查询、更新状态）</li>
 *   <li>待处理申请查询（收件箱）</li>
 *   <li>防重复申请校验</li>
 * </ul>
 *
 * <h3>申请状态说明</h3>
 * <ul>
 *   <li>status=0: 待处理(Pending)</li>
 *   <li>status=1: 已同意(Accepted)</li>
 *   <li>status=2: 已拒绝(Rejected)</li>
 * </ul>
 *
 * <h3>表依赖</h3>
 * <ul>
 *   <li>{@code friend_requests} - 好友申请表</li>
 * </ul>
 *
 * @see FriendRequest
 */
@Mapper
public interface FriendRequestMapper {

    /**
     * 插入好友申请记录
     *
     * <p>初始状态为 0（待处理），自动设置 create_time 和 update_time。
     *
     * @param request 好友申请对象（包含 senderId、receiverId）
     * @return 影响行数（正常为 1）
     */
    int insert(FriendRequest request);

    /**
     * 更新好友申请状态
     *
     * <p>用于同意/拒绝申请，同时更新 update_time。
     *
     * @param id     申请内部 ID
     * @param status 新状态（1=已同意，2=已拒绝）
     * @return 影响行数
     */
    int updateStatus(@Param("id") Integer id, @Param("status") Integer status);

    /**
     * 根据ID查询好友申请
     *
     * <p>用于处理申请时的前置校验。
     *
     * @param id 申请内部 ID
     * @return 好友申请对象，不存在返回 null
     */
    FriendRequest selectById(Integer id);

    /**
     * 查询发送给指定用户的待处理申请
     *
     * <p>用于好友申请收件箱展示，按创建时间降序排列。
     *
     * @param userId 接收者用户内部 ID
     * @return 待处理申请列表
     */
    List<FriendRequest> selectPendingByReceiverId(Integer userId);

    /**
     * 查询两个用户之间的待处理申请
     *
     * <p>用于防重复申请校验：A 向 B 发送申请前，检查是否已有待处理申请。
     *
     * @param senderId   发送者内部 ID
     * @param receiverId 接收者内部 ID
     * @return 待处理申请对象，不存在返回 null
     */
    FriendRequest selectPendingBySenderAndReceiver(@Param("senderId") Integer senderId, @Param("receiverId") Integer receiverId);
    
    /**
     * 查询两个用户之间的所有历史申请
     *
     * <p>双向查询（A→B 或 B→A），用于历史记录查看。
     *
     * @param u1 用户1的内部 ID
     * @param u2 用户2的内部 ID
     * @return 历史申请列表
     */
    List<FriendRequest> selectHistoryBetweenUsers(@Param("u1") Integer u1, @Param("u2") Integer u2);
}
