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
     * 根据 UId 添加正式用户（转正）
     *
     * @param formalUser 正式用户对象（包含 userUId）
     */
    void addByUId(FormalUser formalUser);
}
