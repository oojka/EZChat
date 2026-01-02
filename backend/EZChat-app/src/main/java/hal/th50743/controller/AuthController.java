package hal.th50743.controller;

import hal.th50743.exception.ErrorCode;
import hal.th50743.pojo.*;
import hal.th50743.service.AuthService;
import hal.th50743.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证控制器
 * <p>
 * 处理用户登录、注册和访客加入等认证相关请求。
 * 这些接口路径在WebConfig中被排除在Token拦截器之外。
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    // 自动注入认证服务
    private final AuthService authService;
    private final UserService userService;


    /**
     * 用户登录接口。
     *
     * @param loginReq 包含用户名和密码的登录请求体。
     * @return 登录成功返回包含Token等信息的结果，失败则返回错误信息。
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginReq loginReq){
        // 调用认证服务执行登录逻辑
        LoginVO res = authService.login(loginReq);
        // 如果返回结果不为null，表示登录成功
        if (res != null) {
            // 返回成功响应，并携带用户信息及Token
            return Result.success(res);
        }
        // 登录失败，返回错误信息
        return Result.error(ErrorCode.INVALID_CREDENTIALS);
    }

    /**
     * 正式用户注册接口。
     *
     * @param formalUserRegisterReq 包含用户注册信息的请求体。
     * @return 注册成功返回包含Token等信息的结果。
     */
    @PostMapping("/register")
    public Result register(@RequestBody FormalUserRegisterReq formalUserRegisterReq){
        // 调用认证服务执行用户注册逻辑
        LoginVO res = authService.userRegister(formalUserRegisterReq);
        // 注册成功后，直接返回成功响应
        return Result.success(res);
    }

    /**
     * 注册时上传头像接口
     *
     * @param file 头像文件
     * @return 上传成功后的图片信息
     */
    @PostMapping("/register/upload")
    public Result upload(@RequestParam("file") MultipartFile file) {
        Image image = userService.uploadAvatar(file);
        return Result.success(image);
    }
    /**
     * 访客加入聊天接口。
     *
     * @param guestReq 包含访客昵称和聊天码的请求体。
     * @return 加入成功返回包含Token等信息的结果，失败则返回错误信息。
     */
    @PostMapping("/guest")
    public Result guestRegister(@RequestBody GuestReq guestReq){
        // 调用认证服务处理访客加入逻辑
        LoginVO res = authService.guest(guestReq);
        // 如果返回结果为null，表示加入失败
        if (res == null) {
            // 记录访客加入失败的日志
            log.info("guest join chat:'{}' failed", guestReq.getChatCode());
            // 返回失败响应
            return Result.error(ErrorCode.SYSTEM_ERROR, "guest join chat failed");
        }
        // 加入成功，返回成功响应
        return Result.success(res);
    }

    /**
     * 邀请码免密加入（访客）
     *
     * 业务目的：
     * - 创建短链接的邀请码后，任何人都可通过 inviteCode 免密加入房间（前提：join_enabled=1）
     * - 成功后返回 JWT token（前端写入 localStorage 后可进入 /chat）
     *
     * @param req 邀请加入请求
     * @return 统一响应结果（LoginVO）
     */
    @PostMapping("/invite")
    public Result inviteGuest(@RequestBody InviteGuestReq req) {
        LoginVO res = authService.inviteGuest(req);
        return Result.success(res);
    }


}
