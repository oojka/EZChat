package hal.th50743.controller;

import hal.th50743.pojo.AppInitVO;
import hal.th50743.pojo.Result;
import hal.th50743.service.ChatService;
import hal.th50743.utils.CurrentHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用初始化控制器
 *
 * <p>提供应用初始化相关的 RESTful API 端点，用于前端首屏快速加载。
 *
 * <h3>API 列表</h3>
 * <ul>
 *   <li>GET /init/chat-list - 获取聊天室列表（轻量版）</li>
 * </ul>
 *
 * <h3>认证要求</h3>
 * <p>所有接口需要 Header: token
 *
 * @see ChatService
 */
@Slf4j
@RestController
@RequestMapping("/init")
@RequiredArgsConstructor
public class AppInitController {

    private final ChatService chatService;

    /**
     * 获取聊天室列表
     *
     * <p>轻量版初始化接口，仅返回 AsideList 必要字段，不携带成员列表。
     * 用于 refresh 首屏快速加载。
     *
     * @return 初始化数据（聊天室列表）
     */
    @GetMapping("/chat-list")
    public Result<AppInitVO> getChatListInit() {
        Integer userId = CurrentHolder.getCurrentId();
        AppInitVO res = chatService.getChatVOListAndMemberStatusListLite(userId);
        return Result.success(res);
    }

}
