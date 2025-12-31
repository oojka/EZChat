package hal.th50743.service.impl;

import hal.th50743.pojo.AppInitVO;
import hal.th50743.service.AppInitService;
import hal.th50743.service.ChatService;
import hal.th50743.utils.CurrentHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 应用初始化服务实现类
 * <p>
 * 负责处理应用启动时的初始化数据获取。
 */
@Service
@RequiredArgsConstructor
public class AppInitServiceImpl implements AppInitService {

    private final ChatService chatService;

    /**
     * 获取初始化状态
     * <p>
     * 获取当前用户的聊天列表及成员在线状态。
     *
     * @return AppInitVO 初始化数据视图对象
     */
    @Override
    public AppInitVO getInitState() {
        Integer userId = CurrentHolder.getCurrentId();
        return chatService.getChatVOListAndMemberStatusList(userId);
    }
}
