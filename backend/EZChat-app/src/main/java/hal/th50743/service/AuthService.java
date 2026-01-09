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
     * 访客加入聊天室（支持头像）
     * <p>
     * 支持两种验证模式：
     * 1. 密码模式：chatCode + password + nickName + avatar
     * 2. 邀请码模式：inviteCode + nickName + avatar
     * <p>
     * 业务流程：
     * 1. 验证请求参数
     * 2. 处理头像（关联现有或上传新）
     * 3. 创建用户记录
     * 4. 加入聊天室
     * 5. 生成 JWT token
     *
     * @param req 访客加入请求（包含头像）
     * @return 登录成功后的视图对象（包含 Token）
     */
    LoginVO joinChat(GuestJoinReq req);

    /**
     * RefreshToken 兑换 AccessToken
     *
     * @param req RefreshToken 请求对象
     * @return LoginVO 返回新的 AccessToken
     */
    LoginVO refreshToken(RefreshTokenReq req);



}
