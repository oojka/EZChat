package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发送消息请求对象
 * <p>
 * 用于接收 WebSocket 发送消息时的请求参数。
 * 支持纯文本、纯图片及混合消息类型。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageReq {

    /**
     * 发送者用户对外ID（users.uid）
     */
    private String sender;

    /**
     * 目标聊天室代码（chats.chat_code）
     */
    private String chatCode;

    /**
     * 消息文本内容
     * <p>
     * 业务规则：纯图片消息时可为空
     */
    private String text;

    /**
     * 消息图片列表
     * <p>
     * 业务规则：纯文本消息时可为空
     */
    private List<Image> images;

    /**
     * 消息创建时间（客户端时间戳）
     */
    private LocalDateTime createTime;

    /**
     * 临时消息ID（客户端生成）
     * <p>
     * 业务说明：用于客户端消息确认（ACK）机制，
     * 服务端处理后会返回此ID以便客户端匹配本地消息
     */
    private String tempId;

}
