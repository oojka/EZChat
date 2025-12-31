package hal.th50743.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import hal.th50743.pojo.WebSocketResult;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息工具类
 * <p>
 * 负责 WebSocket 消息的序列化与封装。
 */
@Slf4j
public class MessageUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 序列化 WebSocketResult 对象为 JSON 字符串
     *
     * @param webSocketResult WebSocket结果对象
     * @return JSON 字符串
     */
    public static String setMessage(WebSocketResult webSocketResult) {
        String message = null;
        try {
            message = objectMapper.writeValueAsString(webSocketResult);
        } catch (JsonProcessingException e) {
            log.error("failed to serialize message:{} '{}'", webSocketResult, e.getMessage());
            //TODO 全局异常处理器处理
        }
        return message;
    }

    /**
     * 封装并序列化 WebSocket 消息
     *
     * @param isSystemMessage 是否是系统消息
     * @param type            消息类型
     * @param data            消息数据
     * @return JSON 字符串
     */
    public static String setMessage(boolean isSystemMessage, String type, Object data) {
        String message = null;
        WebSocketResult result = new WebSocketResult();
        if (isSystemMessage) {
            result.setIsSystemMessage(1);
        } else {
            result.setIsSystemMessage(0);
        }
        result.setData(data);
        if (type != null) {
            result.setType(type);
        }
        if (data != null) {
            result.setData(data);
        }
        try {
            message = objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("failed to serialize message:{} ,'{}'", result, e.getMessage());
            //TODO 全局异常处理器处理
        }
        return message;
    }
}
