package hal.th50743.mapper;

import hal.th50743.pojo.Message;
import hal.th50743.pojo.MessageVO;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 消息 Mapper 接口
 * <p>
 * 负责消息相关的数据库操作。
 */
@Mapper
public interface MessageMapper {

    /**
     * 插入消息
     *
     * @param message 消息对象
     */
    void insertMessage(Message message);

    /**
     * 获取用户所有聊天室的未读消息数
     *
     * @param userId 用户ID
     * @return 包含 chatCode 和 unreadCount 的 Map 列表
     */
    @MapKey("chatCode")
    List<Map<String, Object>> selectUnreadCountMapByUserId(Integer userId);

    /**
     * 获取用户在指定聊天室的未读消息数
     *
     * @param userId 用户ID
     * @param chatId 聊天室ID
     * @return 未读消息数
     */
    Integer selectUnreadCountMapByUserIdAndChatId(Integer userId, Integer chatId);

    /**
     * 获取用户所有聊天室的最后一条消息
     *
     * @param userId 用户ID
     * @return 键为 chatCode，值为 MessageVO 的 Map
     */
    @MapKey("chatCode")
    Map<String, MessageVO> selectLastMessageListByUserId(Integer userId);

    /**
     * 获取指定聊天室的最后一条消息
     *
     * @param chatId 聊天室ID
     * @return 消息视图对象
     */
    MessageVO selectLastMessageByChatId(Integer chatId);

    /**
     * 获取指定聊天室的消息列表（支持时间戳分页）
     *
     * @param chatId    聊天室ID
     * @param timeStamp 时间戳（可选）
     * @return 消息视图对象列表
     */
    List<MessageVO> selectMessageListByChatIdAndTimeStamp(@Param("chatId") Integer chatId,
            @Param("timeStamp") LocalDateTime timeStamp);

}
