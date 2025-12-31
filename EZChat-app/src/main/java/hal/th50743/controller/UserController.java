package hal.th50743.controller;

import hal.th50743.pojo.Result;
import hal.th50743.pojo.UserReq;
import hal.th50743.service.UserService;
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

    /**
     * 获取用户信息
     *
     * @param uId 用户唯一标识
     * @return 包含用户信息的统一响应结果
     */
    @GetMapping("/{uId}")
    public Result getUserInfo(@PathVariable String uId) {
        log.info("get user info, uid={}", uId);
        return Result.success(userService.getUserInfoByUId(uId));
    }

    /**
     * 更新用户信息
     * @param userReq 包含待更新信息的用户请求对象
     */
    @PostMapping
    public void updateUserInfo(@RequestBody UserReq userReq) {
        userReq.setUserId(CurrentHolder.getCurrentId());
        log.info("update user info, user={}", userReq);
        userService.update(userReq);
    }

}
