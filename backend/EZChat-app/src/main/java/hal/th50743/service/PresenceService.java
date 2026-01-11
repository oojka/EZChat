package hal.th50743.service;

import jakarta.websocket.Session;

import java.util.Map;

/**
 * 在线状态服务接口
 * <p>
 * 管理用户的在线状态，提供在线状态查询功能。
 * <p>
 * 实现说明：
 * <ul>
 *     <li>用户上线时由 WebSocket 连接触发状态变更</li>
 *     <li>用户下线时有 30 秒的防抖缓冲，避免网络波动造成状态闪烁</li>
 *     <li>在线状态会广播给该用户所在的所有聊天室成员</li>
 * </ul>
 */
public interface PresenceService {

    /**
     * 判断用户是否在线
     *
     * @param userId 用户ID
     * @return true=在线，false=离线
     */
    boolean isOnline(Integer userId);

    /**
     * 获取在线用户映射表
     * <p>
     * 返回当前所有在线用户的 WebSocket Session 映射。
     * 注意：此方法返回的是实时数据的视图，请勿直接修改。
     *
     * @return 在线用户映射表（userId -> WebSocket Session）
     */
    Map<Integer, Session> getOnlineUsers();
}
