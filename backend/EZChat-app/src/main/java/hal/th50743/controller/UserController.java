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
 *
 * <p>提供用户信息管理相关的 RESTful API 端点。
 *
 * <h3>API 列表</h3>
 * <ul>
 *   <li>GET /user/{uid} - 获取用户信息</li>
 *   <li>POST /user - 更新用户信息</li>
 *   <li>POST /user/profile - 修改个人资料</li>
 *   <li>POST /user/upgrade - 访客升级为正式用户</li>
 *   <li>PUT /user/password - 修改密码</li>
 * </ul>
 *
 * <h3>认证要求</h3>
 * <p>所有接口需要 Header: token
 *
 * @see UserService
 * @see AuthService
 * @see FormalUserService
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
     * <p>根据用户 UID 获取用户的公开信息。
     *
     * @param uid 用户对外唯一标识
     * @return 用户信息
     */
    @GetMapping("/{uid}")
    public Result<UserVO> getUserInfo(@PathVariable String uid) {
        log.info("get user info, uid={}", uid);
        return Result.success(userService.getUserInfoByUid(uid));
    }

    /**
     * 更新用户信息
     *
     * <p>更新当前登录用户的信息（通用更新接口）。
     *
     * @param userReq 包含待更新信息的请求对象
     */
    @PostMapping
    public void updateUserInfo(@RequestBody UserReq userReq) {
        userReq.setUserId(CurrentHolder.getCurrentId());
        log.info("update user info, user={}", userReq);
        userService.update(userReq);
    }

    /**
     * 修改用户个人资料
     *
     * <p>更新当前用户的个人资料（昵称、头像、简介等），不包含账号与密码。
     *
     * @param userReq 个人资料更新请求
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
     * 访客升级为正式用户
     *
     * <p>将当前登录的访客用户升级为正式用户，创建用户名和密码账号。
     * 升级成功后返回新的登录凭证。
     *
     * @param req 注册请求（用户名、密码、昵称、头像等）
     * @return 新的登录凭证
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
     * 修改密码
     *
     * <p>仅限正式用户调用，验证旧密码后更新为新密码。
     *
     * @param req 密码修改请求（旧密码、新密码）
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
