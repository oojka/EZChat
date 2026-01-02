package hal.th50743.service;

import hal.th50743.pojo.*;

/**
 * 认证服务接口
 * <p>
 * 定义用户登录、注册及访客准入的业务逻辑。
 */
public interface AuthService {

    /**
     * 用户登录
     *
     * @param loginReq 登录请求参数
     * @return 登录成功后的视图对象（包含 Token）
     */
    LoginVO login(LoginReq loginReq);

    /**
     * 正式用户注册
     *
     * @param formalUserRegisterReq 注册请求参数
     * @return 注册成功后的视图对象（包含 Token）
     */
    LoginVO userRegister(FormalUserRegisterReq formalUserRegisterReq);

    /**
     * 访客登录
     *
     * @param guestReq 访客请求参数
     * @return 登录成功后的视图对象（包含 Token）
     */
    LoginVO guest(GuestReq guestReq);

    /**
     * 邀请码免密加入（访客）
     *
     * 业务规则：
     * - 邀请码有效：免密加入（即使房间设置了密码）
     * - join_enabled=0：邀请码也不可加入
     * - 成功后返回 JWT（供前端进入 /chat 体系）
     *
     * @param req 邀请加入请求
     * @return LoginVO（包含 token）
     */
    LoginVO inviteGuest(InviteGuestReq req);

}
