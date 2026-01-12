package hal.th50743.controller;

import hal.th50743.exception.ErrorCode;
import hal.th50743.pojo.*;
import hal.th50743.service.AuthService;
import hal.th50743.service.ChatService;
import hal.th50743.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证控制器
 *
 * <p>提供用户认证相关的 RESTful API 端点，包括登录、注册、访客加入等功能。
 *
 * <h3>API 列表</h3>
 * <ul>
 *   <li>POST /auth/login - 用户登录</li>
 *   <li>POST /auth/register - 正式用户注册</li>
 *   <li>POST /auth/register/upload - 注册时上传头像</li>
 *   <li>POST /auth/validate - 验证聊天室加入请求</li>
 *   <li>POST /auth/join - 访客加入聊天室</li>
 *   <li>POST /auth/refresh - 刷新访问令牌</li>
 * </ul>
 *
 * <h3>认证要求</h3>
 * <p>本控制器下所有接口无需 token，路径在 WebConfig 中被排除在拦截器之外。
 *
 * @see AuthService
 * @see UserService
 * @see ChatService
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    // 自动注入认证服务
    private final AuthService authService;
    private final UserService userService;
    private final ChatService chatService;

    /**
     * 用户登录
     *
     * <p>正式用户通过用户名和密码登录系统。
     *
     * @param loginReq 登录请求（用户名、密码）
     * @return 登录成功返回令牌信息，失败返回错误
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginReq loginReq) {
        // 调用认证服务执行登录逻辑
        LoginVO res = authService.login(loginReq);
        // 如果返回结果不为null，表示登录成功
        if (res != null) {
            // 返回成功响应，并携带用户信息及Token
            return Result.success(res);
        }
        // 登录失败，返回错误信息
        // 注意：Result.error() 返回 Result<?>，需要类型转换（错误情况下 data 为 null，类型参数无实际意义）
        @SuppressWarnings("unchecked")
        Result<LoginVO> errorResult = (Result<LoginVO>) Result.error(ErrorCode.INVALID_CREDENTIALS);
        return errorResult;
    }

    /**
     * 正式用户注册
     *
     * <p>创建新的正式用户账号，注册成功后自动登录。
     *
     * @param formalUserRegisterReq 注册请求（用户名、密码、昵称等）
     * @return 注册成功返回令牌信息
     */
    @PostMapping("/register")
    public Result<LoginVO> register(@RequestBody FormalUserRegisterReq formalUserRegisterReq) {
        // 调用认证服务执行用户注册逻辑
        LoginVO res = authService.userRegister(formalUserRegisterReq);
        // 注册成功后，直接返回成功响应
        return Result.success(res);
    }

    /**
     * 注册时上传头像
     *
     * <p>在注册流程中预上传用户头像。
     *
     * @param file 头像图片文件
     * @return 上传成功的图片信息
     */
    @PostMapping("/register/upload")
    public Result<Image> upload(@RequestParam("file") MultipartFile file) {
        Image image = userService.uploadAvatar(file);
        return Result.success(image);
    }

    /**
     * 验证聊天室加入请求
     *
     * <p>轻量级预验证接口，仅验证房间是否存在、密码是否正确，不执行实际加入操作。
     * 支持两种验证模式：
     * <ul>
     *   <li>密码模式：chatCode + password</li>
     *   <li>邀请码模式：inviteCode</li>
     * </ul>
     *
     * @param req 验证请求
     * @return 验证通过返回聊天室简要信息
     */
    @PostMapping("/validate")
    public Result<ChatVO> validateChatJoin(@RequestBody ValidateChatJoinReq req) {
        ChatVO chatVO = chatService.validateChatJoin(req);
        return Result.success(chatVO);
    }

    /**
     * 访客加入聊天室
     *
     * <p>访客用户加入指定聊天室，支持两种验证模式：
     * <ul>
     *   <li>密码模式：chatCode + password + nickName</li>
     *   <li>邀请码模式：inviteCode + nickName</li>
     * </ul>
     *
     * @param req 访客加入请求
     * @return 登录凭证信息
     */
    @PostMapping("/join")
    public Result<LoginVO> joinChat(@RequestBody GuestJoinReq req) {
        LoginVO res = authService.joinChat(req);
        return Result.success(res);
    }

    /**
     * 刷新访问令牌
     *
     * <p>使用 RefreshToken 兑换新的 AccessToken。
     *
     * @param req 刷新请求（RefreshToken）
     * @return 新的令牌信息
     */
    @PostMapping("/refresh")
    public Result<LoginVO> refreshToken(@RequestBody RefreshTokenReq req) {
        LoginVO res = authService.refreshToken(req);
        return Result.success(res);
    }
}
