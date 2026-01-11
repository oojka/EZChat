package hal.th50743.mapper;

import hal.th50743.pojo.FriendRequest;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 好友申请 Mapper 接口
 * <p>
 * 负责好友申请（friend_requests 表）的数据库操作。
 * 申请状态：0=待处理(Pending)，1=已同意(Accepted)，2=已拒绝(Rejected)
 */
@Mapper
public interface FriendRequestMapper {

    /**
     * 插入好友申请记录
     *
     * @param request 好友申请对象
     * @return 影响行数
     */
    @Insert("INSERT INTO friend_requests (sender_id, receiver_id, status, create_time, update_time) VALUES (#{senderId}, #{receiverId}, 0, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FriendRequest request);

    /**
     * 更新好友申请状态
     *
     * @param id     申请ID
     * @param status 状态（0=待处理，1=已同意，2=已拒绝）
     * @return 影响行数
     */
    @Update("UPDATE friend_requests SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Integer id, @Param("status") Integer status);

    /**
     * 根据ID查询好友申请
     *
     * @param id 申请ID
     * @return 好友申请对象
     */
    @Select("SELECT * FROM friend_requests WHERE id = #{id}")
    FriendRequest selectById(Integer id);

    /**
     * 查询发送给指定用户的待处理申请
     *
     * @param userId 接收者用户ID
     * @return 待处理申请列表
     */
    @Select("SELECT * FROM friend_requests WHERE receiver_id = #{userId} AND status = 0 ORDER BY create_time DESC")
    List<FriendRequest> selectPendingByReceiverId(Integer userId);

    /**
     * 查询两个用户之间的待处理申请
     *
     * @param senderId   发送者ID
     * @param receiverId 接收者ID
     * @return 待处理申请对象，不存在返回 null
     */
    @Select("SELECT * FROM friend_requests WHERE sender_id = #{senderId} AND receiver_id = #{receiverId} AND status = 0")
    FriendRequest selectPendingBySenderAndReceiver(@Param("senderId") Integer senderId, @Param("receiverId") Integer receiverId);
    
    /**
     * 查询两个用户之间的所有历史申请
     *
     * @param u1 用户1的ID
     * @param u2 用户2的ID
     * @return 历史申请列表
     */
    @Select("SELECT * FROM friend_requests WHERE (sender_id = #{u1} AND receiver_id = #{u2}) OR (sender_id = #{u2} AND receiver_id = #{u1})")
    List<FriendRequest> selectHistoryBetweenUsers(@Param("u1") Integer u1, @Param("u2") Integer u2);
}
