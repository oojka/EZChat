package hal.th50743.mapper;

import hal.th50743.pojo.FormalUser;
import hal.th50743.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户 Mapper 接口
 * <p>
 * 负责用户相关的数据库操作。
 */
@Mapper
public interface UserMapper {

    /**
     * 插入用户
     *
     * @param user 用户对象
     */
    void insertUser(User user);

    /**
     * 插入正式用户
     *
     * @param formalUser 正式用户对象
     */
    void insertFormalUser(FormalUser formalUser);

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
    Integer selectIdByUid(String uid);

    /**
     * 更新用户信息
     *
     * @param u 用户对象
     */
    void update(User u);

    /**
     * 更新用户逻辑删除标记
     *
     * @param userId    用户ID
     * @param isDeleted 删除标记
     */
    void updateUserDeleted(@Param("userId") Integer userId, @Param("isDeleted") Integer isDeleted);

    /**
     * 更新用户类型
     *
     * @param userId   用户ID
     * @param userType 用户类型
     */
    void updateUserType(@Param("userId") Integer userId, @Param("userType") Integer userType);

    /**
     * 更新正式用户 RefreshToken
     *
     * @param userId 用户ID
     * @param token  RefreshToken
     */
    void updateFormalUserToken(@Param("userId") Integer userId, @Param("token") String token);

    /**
     * 获取正式用户 RefreshToken
     *
     * @param userId 用户ID
     * @return RefreshToken
     */
    String selectFormalUserTokenByUserId(@Param("userId") Integer userId);

    /**
     * 查询需要清理的访客用户ID列表
     *
     * @param cutoff 离线截止时间
     * @return 访客用户ID列表
     */
    List<Integer> selectGuestCandidatesForCleanup(@Param("cutoff") LocalDateTime cutoff);

    /**
     * 根据 ID 获取用户视图对象（已废弃，建议使用 selectUserByUid）
     *
     * @param id 用户 ID
     * @return 用户对象
     */
    User selectUserVOById(Integer id);

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
    User selectUserByUid(String uid);

    /**
     * 根据 UID 查询用户信息（包含头像 object_name）
     *
     * @param uid 用户 UID
     * @return 用户实体（包含 avatarObjectName 临时字段）
     */
    User selectByUidWithAvatar(String uid);

    /**
     * 根据 ID 查询用户信息（包含头像 object_name）
     *
     * @param id 用户 ID
     * @return 用户实体（包含 avatarObjectName 临时字段）
     */
    User selectByIdWithAvatar(Integer id);

    /**
     * 根据用户ID获取用户名
     *
     * @param userId 用户ID
     * @return 用户名（正式用户）或 null（访客用户）
     */
    String selectUsernameByUserId(Integer userId);

    /**
     * 根据用户ID获取用户信息
     *
     * @param userId 用户ID
     * @return 用户对象
     */
    User selectUserById(Integer userId);

    /**
     * 更新正式用户密码
     *
     * @param userId       用户ID
     * @param passwordHash 新的密码哈希
     */
    void updateFormalUserPassword(@Param("userId") Integer userId, @Param("passwordHash") String passwordHash);

    /**
     * 根据用户ID获取密码哈希
     *
     * @param userId 用户ID
     * @return 密码哈希
     */
    String selectPasswordHashByUserId(@Param("userId") Integer userId);
}
