package hal.th50743.service;

import jakarta.websocket.Session;

import java.util.Map;

/**
 * 在线状态服务接口
 */
public interface PresenceService {

    /**
     * 判断用户是否在线
     *
     * @param userId 用户ID
     * @return 是否在线
     */
    boolean isOnline(Integer userId);

    /**
     * 获取在线用户映射表
     *
     * @return 在线用户映射表
     */
    Map<Integer, Session> getOnlineUsers();
}
