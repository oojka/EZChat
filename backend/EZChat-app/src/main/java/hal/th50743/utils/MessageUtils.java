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
            // TODO 全局异常处理器处理
        }
        return message;
    }

    /**
     * 封装并序列化 WebSocket 消息 (Support Status Code)
     *
     * @param code 状态码 (1xxx:Msg, 2xxx:Sys, 3xxx:Biz)
     * @param type 消息类型 (String)
     * @param data 消息数据
     * @return JSON 字符串
     */
    public static String setMessage(Integer code, String type, Object data) {
        String message = null;
        WebSocketResult result = new WebSocketResult();
        result.setCode(code);
        result.setType(type);
        result.setData(data);

        // 兼容旧字段 isSystemMessage (Optional)
        // 2xxx 认为是系统消息
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
     * @deprecated Use setMessage(Integer code, String type, Object data)
     */
    @Deprecated
    public static String setMessage(boolean isSystemMessage, String type, Object data) {
        return setMessage(isSystemMessage ? 2000 : 1000, type, data);
    }
}
