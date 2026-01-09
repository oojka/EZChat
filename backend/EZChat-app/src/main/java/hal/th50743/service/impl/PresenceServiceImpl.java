package hal.th50743.service.impl;

import hal.th50743.service.PresenceService;
import hal.th50743.ws.WebSocketServer;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 在线状态服务实现
 */
@Slf4j
@Service
public class PresenceServiceImpl implements PresenceService {

    /**
     * 判断用户是否在线
     *
     * @param userId 用户ID
     * @return 是否在线
     */
    @Override
    public boolean isOnline(Integer userId) {
        if (userId == null) {
            return false;
        }
        return WebSocketServer.getOnLineUserList().containsKey(userId);
    }

    /**
     * 获取在线用户映射表
     *
     * @return 在线用户映射表
     */
    @Override
    public Map<Integer, Session> getOnlineUsers() {
        return WebSocketServer.getOnLineUserList();
    }
}
