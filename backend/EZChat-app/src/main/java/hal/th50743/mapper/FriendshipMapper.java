package hal.th50743.mapper;

import hal.th50743.pojo.Friendship;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 好友关系 Mapper 接口
 * <p>
 * 负责好友关系（friendships 表）的数据库操作。
 * 好友关系是双向存储的：A 和 B 成为好友时，会同时插入两条记录。
 */
@Mapper
public interface FriendshipMapper {

    /**
     * 插入好友关系记录
     *
     * @param friendship 好友关系对象
     * @return 影响行数
     */
    @Insert("INSERT INTO friendships (user_id, friend_id, alias, create_time) VALUES (#{userId}, #{friendId}, #{alias}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Friendship friendship);

    /**
     * 双向删除好友关系
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     * @return 影响行数
     */
    @Delete("DELETE FROM friendships WHERE (user_id = #{userId} AND friend_id = #{friendId}) OR (user_id = #{friendId} AND friend_id = #{userId})")
    int deleteBiDirectional(@Param("userId") Integer userId, @Param("friendId") Integer friendId);

    /**
     * 查询用户的所有好友关系
     *
     * @param userId 用户ID
     * @return 好友关系列表
     */
    @Select("SELECT * FROM friendships WHERE user_id = #{userId}")
    List<Friendship> selectByUserId(Integer userId);

    /**
     * 查询两个用户之间是否存在好友关系
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     * @return 好友关系对象，不存在返回 null
     */
    @Select("SELECT * FROM friendships WHERE user_id = #{userId} AND friend_id = #{friendId}")
    Friendship selectByUserIdAndFriendId(@Param("userId") Integer userId, @Param("friendId") Integer friendId);

    /**
     * 更新好友备注名
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     * @param alias    新的备注名
     * @return 影响行数
     */
    @Update("UPDATE friendships SET alias = #{alias} WHERE user_id = #{userId} AND friend_id = #{friendId}")
    int updateAlias(@Param("userId") Integer userId, @Param("friendId") Integer friendId, @Param("alias") String alias);
}
