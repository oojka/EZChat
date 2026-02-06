package hal.th50743.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import hal.th50743.pojo.WebSocketResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 消息工具类单元测试
 *
 * <p>覆盖以下场景:</p>
 * <ul>
 *     <li>系统消息标志设置</li>
 *     <li>非系统消息标志设置</li>
 *     <li>WebSocketResult对象序列化</li>
 * </ul>
 *
 * @see MessageUtils
 */
class MessageUtilsTest {

    /**
     * 设置消息 - 系统消息码(2xxx)自动标记为系统消息
     */
    @Test
    void setMessageFlagsSystemMessages() throws Exception {
        String json = MessageUtils.setMessage(2001, "TYPE", "payload");

        WebSocketResult result = new ObjectMapper().readValue(json, WebSocketResult.class);

        assertEquals(Integer.valueOf(1), result.getIsSystemMessage());
        assertEquals(Integer.valueOf(2001), result.getCode());
        assertEquals("TYPE", result.getType());
    }

    /**
     * 设置消息 - 非系统消息码(1xxx)标记为非系统消息
     */
    @Test
    void setMessageFlagsNonSystemMessages() throws Exception {
        String json = MessageUtils.setMessage(1001, "TYPE", "payload");

        WebSocketResult result = new ObjectMapper().readValue(json, WebSocketResult.class);

        assertEquals(Integer.valueOf(0), result.getIsSystemMessage());
        assertEquals(Integer.valueOf(1001), result.getCode());
    }

    /**
     * 设置消息 - 从WebSocketResult对象序列化为JSON字符串
     */
    @Test
    void setMessageFromResultSerializes() {
        WebSocketResult result = new WebSocketResult(0, 1000, "TYPE", "payload");

        String json = MessageUtils.setMessage(result);

        assertNotNull(json);
    }
}
