package hal.th50743.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import hal.th50743.pojo.WebSocketResult;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 消息工具类
 *
 * <p>负责 WebSocket 消息的序列化与封装，统一消息格式。
 *
 * <h3>消息格式</h3>
 * <pre>{@code
 * {
 *   "code": 1000,           // 状态码
 *   "type": "message",      // 消息类型
 *   "data": {...},          // 消息数据
 *   "isSystemMessage": 0    // 是否系统消息（兼容旧版）
 * }
 * }</pre>
 *
 * <h3>状态码规范</h3>
 * <table border="1">
 *   <tr><th>范围</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>1xxx</td><td>消息</td><td>普通聊天消息</td></tr>
 *   <tr><td>2xxx</td><td>系统</td><td>系统通知（上线/离线/加入等）</td></tr>
 *   <tr><td>3xxx</td><td>业务</td><td>业务操作结果</td></tr>
 * </table>
 *
 * <h3>消息类型（type）</h3>
 * <ul>
 *   <li>message - 聊天消息</li>
 *   <li>online - 用户上线</li>
 *   <li>offline - 用户离线</li>
 *   <li>join - 成员加入</li>
 *   <li>leave - 成员离开</li>
 * </ul>
 *
 * @see WebSocketResult WebSocket 消息结构
 * @see hal.th50743.ws.WebSocketServer WebSocket 服务端
 */
@Slf4j
public class MessageUtils {

    /**
     * JSON 序列化器
     *
     * <p>配置：
     * <ul>
     *   <li>注册 JavaTimeModule 支持 Java 8 日期时间类型</li>
     *   <li>禁用时间戳格式，使用 ISO-8601 字符串格式</li>
     * </ul>
     */
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 序列化 WebSocketResult 对象为 JSON 字符串
     *
     * @param webSocketResult WebSocket 消息对象
     * @return JSON 字符串，序列化失败时返回 null
     */
    public static String setMessage(WebSocketResult webSocketResult) {
        String message = null;
        try {
            message = objectMapper.writeValueAsString(webSocketResult);
        } catch (JsonProcessingException e) {
            log.error("failed to serialize message:{} '{}'", webSocketResult, e.getMessage());
        }
        return message;
    }

    /**
     * 封装并序列化 WebSocket 消息
     *
     * <p>根据状态码自动判断是否为系统消息（兼容旧版前端）。
     *
     * @param code 状态码（1xxx: 消息, 2xxx: 系统, 3xxx: 业务）
     * @param type 消息类型（message, online, offline 等）
     * @param data 消息数据
     * @return JSON 字符串，序列化失败时返回 null
     */
    public static String setMessage(Integer code, String type, Object data) {
        String message = null;
        WebSocketResult result = new WebSocketResult();
        result.setCode(code);
        result.setType(type);
        result.setData(data);

        // 2xxx 状态码标记为系统消息（兼容旧版前端）
        if (code != null && code >= 2000 && code < 3000) {
            result.setIsSystemMessage(1);
        } else {
            result.setIsSystemMessage(0);
        }

        try {
            message = objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("failed to serialize message:{} ,'{}'", result, e.getMessage());
        }
        return message;
    }

    /**
     * 封装并序列化 WebSocket 消息（旧版兼容方法）
     *
     * @param isSystemMessage 是否系统消息
     * @param type            消息类型
     * @param data            消息数据
     * @return JSON 字符串
     * @deprecated 使用 {@link #setMessage(Integer, String, Object)} 替代
     */
    @Deprecated
    public static String setMessage(boolean isSystemMessage, String type, Object data) {
        return setMessage(isSystemMessage ? 2000 : 1000, type, data);
    }
}
