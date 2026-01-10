package hal.th50743.controller;

import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.pojo.FormalUserRegisterReq;
import hal.th50743.pojo.LoginVO;
import hal.th50743.pojo.Result;
import hal.th50743.pojo.UpdatePasswordReq;
import hal.th50743.pojo.User;
import hal.th50743.pojo.UserReq;
import hal.th50743.pojo.UserVO;
import hal.th50743.service.AuthService;
import hal.th50743.service.FormalUserService;
import hal.th50743.service.UserService;
import org.springframework.validation.annotation.Validated;
import hal.th50743.utils.CurrentHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 * <p>
 * 处理用户信息的查询、更新等请求。
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final FormalUserService formalUserService;

    /**
     * 获取用户信息
     *
     * @param uid 用户唯一标识
     * @return 包含用户信息的统一响应结果
     */
    @GetMapping("/{uid}")
    public Result<UserVO> getUserInfo(@PathVariable String uid) {
        log.info("get user info, uid={}", uid);
        return Result.success(userService.getUserInfoByUid(uid));
    }

    /**
     * 更新用户信息
     * 
     * @param userReq 包含待更新信息的用户请求对象
     */
    @PostMapping
    public void updateUserInfo(@RequestBody UserReq userReq) {
        userReq.setUserId(CurrentHolder.getCurrentId());
        log.info("update user info, user={}", userReq);
        userService.update(userReq);
    }

    /**
     * 修改用户个人信息（不包含账号与密码）
     *
     * @param userReq 个人资料更新请求对象
     * @return 操作结果
     */
    @PostMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UserReq userReq) {
        userReq.setUserId(CurrentHolder.getCurrentId());
        log.info("update user profile, user={}", userReq);
        userService.update(userReq);
        return Result.success();
    }

    /**
     * Guest用户升级为正式用户
     * <p>
     * 将当前登录的Guest用户升级为正式用户，创建用户名和密码账号。
     * 升级成功后返回新的LoginVO（包含新的Token）。
     *
     * @param req 正式用户注册请求对象（包含用户名、密码、昵称、头像等信息）
     * @return 升级成功返回包含新的Token等信息的结果
     */
    @PostMapping("/upgrade")
    public Result<LoginVO> upgradeToFormalUser(@RequestBody FormalUserRegisterReq req) {
        // 获取当前登录用户的ID
        Integer currentUserId = CurrentHolder.getCurrentId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User not authenticated");
        }

        // 验证当前用户是否存在
        User currentUser = userService.getUserById(currentUserId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "Current user not found");
        }

        // 设置userId，用于标识这是临时用户转正（业务逻辑使用内部ID）
        req.setUserId(currentUserId);

        // 调用认证服务执行升级逻辑（情况B：临时用户转为正式用户）
        LoginVO res = authService.userRegister(req);
        log.info("Guest user upgraded to formal user successfully: userId={}, username={}",
                currentUserId, req.getUsername());

        return Result.success(res);
    }

    /**
     * 修改密码（仅限正式用户）
     * <p>
     * 验证旧密码后更新为新密码，成功后强制登出。
     *
     * @param req 修改密码请求对象
     * @return 操作结果
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(@RequestBody @Validated UpdatePasswordReq req) {
        Integer currentUserId = CurrentHolder.getCurrentId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User not authenticated");
        }

        log.info("User changed password: userId={}", currentUserId);
        formalUserService.updatePassword(currentUserId, req.getOldPassword(), req.getNewPassword());

        return Result.success();
    }

}
