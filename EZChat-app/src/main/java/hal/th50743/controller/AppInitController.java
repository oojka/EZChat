package hal.th50743.controller;


import hal.th50743.pojo.AppInitVO;
import hal.th50743.pojo.Result;
import hal.th50743.service.AppInitService;
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

    private final AppInitService appInitService;

    /**
     * 获取应用初始化状态
     *
     * @return Result<AppInitVO> 包含初始化数据的统一响应结果
     */
    @GetMapping
    public Result getInit(){
        AppInitVO res = appInitService.getInitState();
        return Result.success(res);
    }

}
