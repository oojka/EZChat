package hal.th50743.service;

import hal.th50743.pojo.FormalUser;
import hal.th50743.pojo.LoginReq;
import hal.th50743.pojo.User;

/**
 * 正式用户服务接口
 * <p>
 * 定义正式用户的注册、登录及转正业务逻辑。
 */
public interface FormalUserService {

    /**
     * 用户登录
     *
     * @param LoginReq 登录请求参数
     * @return 用户对象
     */
    User login(LoginReq LoginReq);

    /**
     * 添加正式用户
     *
     * @param formalUser 正式用户对象
     */
    void add(FormalUser formalUser);

    /**
     * 根据用户ID获取用户名（仅限正式用户）
     *
     * @param userId 用户 ID
     * @return 用户名，如果非正式用户返回 null
     */
    String getUsernameById(Integer userId);

    /**
     * 根据 UId 添加正式用户（转正）
     * 
     * @deprecated 使用 addByUserId 替代，业务逻辑应使用内部 userId 而非外部 userUid
     * @param formalUser 正式用户对象（包含 userUid）
     */
    @Deprecated
    void addByUId(FormalUser formalUser);

    /**
     * 根据用户ID添加正式用户（转正）
     * <p>
     * 直接使用内部用户ID，不依赖外部标识符。
     *
     * @param formalUser 正式用户对象（包含 userId）
     */
    void addByUserId(FormalUser formalUser);
}
