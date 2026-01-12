package hal.th50743.mapper;

import hal.th50743.pojo.FormalUser;
import hal.th50743.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户数据访问层
 *
 * <p>提供用户相关的数据库操作，支持访客用户和正式用户两种类型。
 *
 * <h3>主要功能</h3>
 * <ul>
 *   <li>用户 CRUD 操作（创建、查询、更新）</li>
 *   <li>正式用户凭证管理（密码、Token）</li>
 *   <li>访客用户清理查询</li>
 *   <li>用户头像关联查询</li>
 * </ul>
 *
 * <h3>表依赖</h3>
 * <ul>
 *   <li>{@code users} - 用户主表（访客/正式共用）</li>
 *   <li>{@code formal_users} - 正式用户凭证表（用户名、密码、Token）</li>
 *   <li>{@code assets} - 资产表（头像关联）</li>
 * </ul>
 *
 * <h3>用户类型说明</h3>
 * <ul>
 *   <li>user_type=0: 访客用户（无用户名密码）</li>
 *   <li>user_type=1: 正式用户（有 formal_users 记录）</li>
 * </ul>
 *
 * @see User
 * @see FormalUser
 */
@Mapper
public interface UserMapper {

    /**
     * 插入用户记录
     *
     * <p>插入后通过 useGeneratedKeys 回填 id。
     * 支持访客和正式用户，区别在于 userType 字段。
     *
     * @param user 用户对象（包含 uid、nickname、userType 等）
     */
    void insertUser(User user);

    /**
     * 插入正式用户凭证
     *
     * <p>需先插入 users 表获取 userId，再插入本表。
     *
     * @param formalUser 正式用户凭证对象（包含 userId、username、passwordHash）
     */
    void insertFormalUser(FormalUser formalUser);

    /**
     * 根据用户名查询正式用户凭证
     *
     * <p>用于登录验证，返回包含密码哈希的完整凭证信息。
     *
     * @param username 用户名（唯一）
     * @return 正式用户凭证对象，不存在返回 null
     */
    FormalUser selectFormalUserByUsername(String username);

    /**
     * 根据用户名查询用户完整信息
     *
     * <p>JOIN formal_users 表，返回包含 username 的用户信息。
     *
     * @param username 用户名
     * @return 用户对象（包含 username），不存在返回 null
     */
    User selectUserByUsername(String username);

    /**
     * 根据 UID 查询用户信息
     *
     * <p>LEFT JOIN formal_users，同时返回 username（如果是正式用户）。
     *
     * @param user_id 用户 UID（8 位对外唯一标识）
     * @return 用户对象
     */
    User selectByUid(String user_id);

    /**
     * 根据 UID 获取用户内部 ID
     *
     * <p>轻量查询，仅返回 id 字段。
     *
     * @param uid 用户 UID
     * @return 用户内部 ID，不存在返回 null
     */
    Integer selectIdByUid(String uid);

    /**
     * 更新用户信息（动态 SQL）
     *
     * <p>仅更新非空字段，使用 MyBatis 动态 SQL。
     * 可更新：nickname、assetId、bio、isDeleted、userType、updateTime。
     *
     * @param u 用户对象（id 必填，其他字段可选）
     */
    void update(User u);

    /**
     * 更新用户逻辑删除标记
     *
     * <p>用于软删除用户（is_deleted=1）。
     *
     * @param userId    用户内部 ID
     * @param isDeleted 删除标记（0=正常，1=已删除）
     */
    void updateUserDeleted(@Param("userId") Integer userId, @Param("isDeleted") Integer isDeleted);

    /**
     * 更新用户类型
     *
     * <p>用于访客转正式用户（user_type: 0 → 1）。
     *
     * @param userId   用户内部 ID
     * @param userType 用户类型（0=访客，1=正式）
     */
    void updateUserType(@Param("userId") Integer userId, @Param("userType") Integer userType);

    /**
     * 更新正式用户 RefreshToken
     *
     * <p>用于登录/刷新 Token 时更新存储的 RefreshToken。
     *
     * @param userId 用户内部 ID
     * @param token  RefreshToken（JWT 字符串）
     */
    void updateFormalUserToken(@Param("userId") Integer userId, @Param("token") String token);

    /**
     * 获取正式用户 RefreshToken
     *
     * <p>用于 Token 刷新时的验证。
     *
     * @param userId 用户内部 ID
     * @return RefreshToken 字符串，不存在返回 null
     */
    String selectFormalUserTokenByUserId(@Param("userId") Integer userId);

    /**
     * 查询需要清理的访客用户ID列表
     *
     * <p>GC 任务使用，筛选条件：
     * <ul>
     *   <li>user_type = 0（访客）</li>
     *   <li>is_deleted = 0（未删除）</li>
     *   <li>last_seen_at < cutoff（超过阈值未活跃）</li>
     *   <li>无 formal_users 记录（未转正）</li>
     * </ul>
     *
     * @param cutoff 离线截止时间
     * @return 待清理的访客用户内部 ID 列表
     */
    List<Integer> selectGuestCandidatesForCleanup(@Param("cutoff") LocalDateTime cutoff);

    /**
     * 根据内部 ID 获取用户信息
     *
     * @param id 用户内部 ID
     * @return 用户对象（包含 username）
     * @deprecated 建议使用 {@link #selectUserById(Integer)}
     */
    User selectUserVOById(Integer id);

    /**
     * 更新用户最后活跃时间
     *
     * <p>WebSocket 心跳/消息时调用，用于在线状态判断和 GC。
     *
     * @param userId 用户内部 ID
     * @param now    当前时间
     */
    void updateLastSeenAt(Integer userId, LocalDateTime now);

    /**
     * 根据 UID 获取用户对象
     *
     * <p>LEFT JOIN formal_users，返回包含 username 的完整信息。
     *
     * @param uid 用户 UID
     * @return 用户对象
     */
    User selectUserByUid(String uid);

    /**
     * 根据 UID 查询用户信息（含头像 assetName）
     *
     * <p>JOIN assets 表，额外返回 avatarObjectName 临时字段。
     * 用于需要头像 URL 的场景。
     *
     * @param uid 用户 UID
     * @return 用户实体（包含 avatarObjectName）
     */
    User selectByUidWithAvatar(String uid);

    /**
     * 根据内部 ID 查询用户信息（含头像 assetName）
     *
     * <p>JOIN assets 表，额外返回 avatarObjectName 临时字段。
     *
     * @param id 用户内部 ID
     * @return 用户实体（包含 avatarObjectName）
     */
    User selectByIdWithAvatar(Integer id);

    /**
     * 根据用户ID获取用户名
     *
     * <p>仅查询 formal_users 表，访客用户返回 null。
     *
     * @param userId 用户内部 ID
     * @return 用户名（正式用户），访客返回 null
     */
    String selectUsernameByUserId(Integer userId);

    /**
     * 根据用户ID获取用户完整信息
     *
     * <p>LEFT JOIN formal_users，返回包含 username 的用户对象。
     *
     * @param userId 用户内部 ID
     * @return 用户对象
     */
    User selectUserById(Integer userId);

    /**
     * 更新正式用户密码
     *
     * <p>用于修改密码功能，同时更新 update_time。
     *
     * @param userId       用户内部 ID
     * @param passwordHash 新的密码 BCrypt 哈希
     */
    void updateFormalUserPassword(@Param("userId") Integer userId, @Param("passwordHash") String passwordHash);

    /**
     * 根据用户ID获取密码哈希
     *
     * <p>用于修改密码时的旧密码验证。
     *
     * @param userId 用户内部 ID
     * @return 密码 BCrypt 哈希
     */
    String selectPasswordHashByUserId(@Param("userId") Integer userId);
}
