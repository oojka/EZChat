package hal.th50743.pojo;

import java.util.List;

/**
 * WebSocket 消息处理结果
 *
 * @param sendList 需要接收消息的用户ID列表
 * @param seqId    保存后的消息序列号
 */
public record WSMessageResult(List<Integer> sendList, Long seqId) {
}
