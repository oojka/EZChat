package hal.th50743.pojo;

import java.util.List;

/**
 * 消息列表响应 VO
 *
 * @param messageList 消息列表
 * @param chatRoom    聊天室信息
 */
public record MessageListVO(List<MessageVO> messageList, ChatVO chatRoom) {
}
