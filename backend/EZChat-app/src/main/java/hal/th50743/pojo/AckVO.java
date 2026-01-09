package hal.th50743.pojo;

import lombok.Builder;

/**
 * WebSocket 消息确认 (ACK) 数据载体
 *
 * @param tempId 客户端发送时生成的临时ID
 * @param seqId  服务端持久化后生成的序列号
 */
@Builder
public record AckVO(String tempId, Long seqId) {
}
