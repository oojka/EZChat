package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 统一响应结果封装类
 * <p>
 * 用于封装所有 WebSocket 推送消息的响应格式。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WebSocketResult {

    /**
     * 是否为系统消息（0=普通消息, 1=系统消息）
     */
    private Integer isSystemMessage;

    /**
     * 消息类型码
     * <p>
     * 业务规则：
     * - 1xxx: 聊天消息类
     * - 2xxx: 系统通知类（心跳、ACK等）
     * - 3xxx: 业务事件类（加入、离开、状态变更等）
     */
    private Integer code;

    /**
     * 消息类型标识（如 MESSAGE, ACK, JOIN, LEAVE 等）
     */
    private String type;

    /**
     * 消息载荷数据
     */
    private Object data;

}
