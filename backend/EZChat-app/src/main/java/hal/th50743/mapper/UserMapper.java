package hal.th50743.mapper;

import hal.th50743.pojo.FormalUser;
import hal.th50743.pojo.User;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * 用户 Mapper 接口
 * <p>
 * 负责用户相关的数据库操作。
 */
@Mapper
public interface UserMapper {

    /**
     * 添加用户
     *
     * @param user 用户对象
     */
    void add(User user);

    /**
     * 添加正式用户
     *
     * @param formalUser 正式用户对象
     */
    void addFormalUser(FormalUser formalUser);

    /**
     * 根据用户名查询正式用户信息（包含密码哈希）
     *
     * @param username 用户名
     * @return 正式用户对象，如果不存在返回 null
     */
    FormalUser selectFormalUserByUsername(String username);

    /**
     * 根据用户名查询用户（包含正式用户信息）
     *
     * @param username 用户名
     * @return 用户对象，如果不存在返回 null
     */
    User selectUserByUsername(String username);

    /**
     * 根据 user_id 查询用户
     *
     * @param user_id 用户唯一标识
     * @return 用户对象
     */
    User selectByUid(String user_id);

    /**
     * 根据 UId 获取用户 User_Id
     *
     * @param uid 用户唯一标识
     * @return 用户 ID
     */
    Integer getIdByUid(String uid);

    /**
     * 更新用户信息
     *
     * @param u 用户对象
     */
    void update(User u);

    /**
     * 根据 ID 获取用户视图对象（已废弃，建议使用 getUserByUid）
     *
     * @param id 用户 ID
     * @return 用户对象
     */
    User getUserVOById(Integer id);

    /**
     * 更新用户最后活跃时间
     *
     * @param userId 用户 ID
     * @param now    当前时间
     */
    void updateLastSeenAt(Integer userId, LocalDateTime now);

    /**
     * 根据 UId 获取用户对象
     *
     * @param uid 用户唯一标识
     * @return 用户对象
     */
    User getUserByUid(String uid);

    /**
     * 根据 UID 查询用户信息（包含头像 object_name）
     *
     * @param uid 用户 UID
     * @return 用户实体（包含 avatarObjectName 临时字段）
     */
    User findByUidWithAvatar(String uid);

    /**
     * 根据 ID 查询用户信息（包含头像 object_name）
     *
     * @param id 用户 ID
     * @return 用户实体（包含 avatarObjectName 临时字段）
     */
    User findByIdWithAvatar(Integer id);

    /**
     * 根据用户ID获取用户名
     *
     * @param userId 用户ID
     * @return 用户名（正式用户）或 null（访客用户）
     */
    String getUsernameByUserId(Integer userId);

    /**
     * 根据用户ID获取用户信息
     *
     * @param userId 用户ID
     * @return 用户对象
     */
    User getUserById(Integer userId);
}
