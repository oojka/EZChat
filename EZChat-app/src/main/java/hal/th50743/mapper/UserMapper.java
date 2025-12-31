package hal.th50743.mapper;

import hal.th50743.pojo.FormalUser;
import hal.th50743.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
     * 根据用户名和密码查询用户
     *
     * @param username 用户名
     * @param password 密码
     * @return 用户对象
     */
    User selectByUsernameAndPassword(@Param("username") String username,
                                     @Param("password") String password);

    /**
     * 根据 UId 查询用户
     *
     * @param userUId 用户唯一标识
     * @return 用户对象
     */
    User selectByUId(String userUId);

    /**
     * 根据 UId 获取用户 ID
     *
     * @param uId 用户唯一标识
     * @return 用户 ID
     */
    Integer getIdByUId(String uId);

    /**
     * 更新用户信息
     *
     * @param u 用户对象
     */
    void update(User u);

    /**
     * 根据 ID 获取用户视图对象（已废弃，建议使用 getUserByUId）
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
     * @param uId 用户唯一标识
     * @return 用户对象
     */
    User getUserByUId(String uId);
}
