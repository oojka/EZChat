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
 * <p>
 * 负责处理应用启动时的初始化请求。
 */
@Slf4j
@RestController
@RequestMapping("/init")
@RequiredArgsConstructor
public class AppInitController {

    private final ChatService chatService;

    /**
     * 获取应用初始化状态（只用于渲染 chatList）
     *
     * 业务目的：
     * - refresh 首屏更快：只返回 AsideList 必要字段，不携带每个群的成员列表
     *
     * @return Result<AppInitVO> 包含初始化数据的统一响应结果
     */
    @GetMapping("/chat-list")
    public Result<AppInitVO> getChatListInit() {
        Integer userId = CurrentHolder.getCurrentId();
        AppInitVO res = chatService.getChatVOListAndMemberStatusListLite(userId);
        return Result.success(res);
    }

}
