package hal.th50743.mapper;

import hal.th50743.pojo.Friendship;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 好友关系数据访问层
 *
 * <p>负责好友关系（friendships 表）的数据库操作。
 *
 * <h3>主要功能</h3>
 * <ul>
 *   <li>好友关系 CRUD 操作（创建、查询、删除）</li>
 *   <li>好友列表查询</li>
 *   <li>好友备注管理</li>
 * </ul>
 *
 * <h3>双向存储策略</h3>
 * <p>好友关系采用双向存储：A 和 B 成为好友时，同时插入两条记录：
 * <ul>
 *   <li>(user_id=A, friend_id=B)</li>
 *   <li>(user_id=B, friend_id=A)</li>
 * </ul>
 * 这样每个用户查询自己的好友列表时只需单表查询。
 *
 * <h3>表依赖</h3>
 * <ul>
 *   <li>{@code friendships} - 好友关系表</li>
 * </ul>
 *
 * @see Friendship
 */
@Mapper
public interface FriendshipMapper {

    /**
     * 插入好友关系记录
     *
     * <p>需调用两次以实现双向存储。自动设置 create_time。
     *
     * @param friendship 好友关系对象（包含 userId、friendId、alias）
     * @return 影响行数（正常为 1）
     */
    int insert(Friendship friendship);

    /**
     * 双向删除好友关系
     *
     * <p>一次 SQL 删除双向记录（A→B 和 B→A）。
     *
     * @param userId   当前用户内部 ID
     * @param friendId 好友用户内部 ID
     * @return 影响行数（正常为 2）
     */
    int deleteBiDirectional(@Param("userId") Integer userId, @Param("friendId") Integer friendId);

    /**
     * 查询用户的所有好友关系
     *
     * <p>返回当前用户视角的好友列表（user_id = 当前用户）。
     *
     * @param userId 当前用户内部 ID
     * @return 好友关系列表
     */
    List<Friendship> selectByUserId(Integer userId);

    /**
     * 查询两个用户之间是否存在好友关系
     *
     * <p>单向查询，用于判断是否已是好友。
     *
     * @param userId   当前用户内部 ID
     * @param friendId 目标用户内部 ID
     * @return 好友关系对象，不是好友返回 null
     */
    Friendship selectByUserIdAndFriendId(@Param("userId") Integer userId, @Param("friendId") Integer friendId);

    /**
     * 更新好友备注名
     *
     * <p>仅更新当前用户对好友的备注，不影响对方。
     *
     * @param userId   当前用户内部 ID
     * @param friendId 好友用户内部 ID
     * @param alias    新的备注名
     * @return 影响行数
     */
    int updateAlias(@Param("userId") Integer userId, @Param("friendId") Integer friendId, @Param("alias") String alias);
}
