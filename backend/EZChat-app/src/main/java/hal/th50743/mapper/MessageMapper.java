package hal.th50743.mapper;

import hal.th50743.pojo.Message;
import hal.th50743.pojo.MessageVO;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 消息数据访问层
 *
 * <p>提供消息相关的数据库操作，包括消息存储、序列号管理、未读统计等。
 *
 * <h3>主要功能</h3>
 * <ul>
 *   <li>消息 CRUD 操作（插入、查询、删除）</li>
 *   <li>聊天室消息序列号管理（自增、查询）</li>
 *   <li>未读消息统计（按用户、按聊天室）</li>
 *   <li>最后一条消息查询（用于列表预览）</li>
 *   <li>基于 seq_id 的游标分页查询</li>
 * </ul>
 *
 * <h3>表依赖</h3>
 * <ul>
 *   <li>{@code messages} - 消息主表</li>
 *   <li>{@code chat_sequences} - 聊天室序列号表（用于消息排序）</li>
 *   <li>{@code chat_members} - 成员关系表（用于未读统计）</li>
 *   <li>{@code users} - 用户表（发送者信息）</li>
 *   <li>{@code chats} - 聊天室表（chatCode 关联）</li>
 * </ul>
 *
 * <h3>分页策略</h3>
 * <p>使用 seq_id 游标分页（非 offset 分页），保证消息顺序稳定。
 *
 * @see Message
 * @see MessageVO
 */
@Mapper
public interface MessageMapper {

    /**
     * 插入消息记录
     *
     * <p>插入后通过 useGeneratedKeys 回填 id。
     * 消息的 seq_id 需在插入前通过 {@link #selectCurrentSequence} 获取。
     *
     * @param message 消息对象（包含 senderId、chatId、seqId、type、text 等）
     */
    void insertMessage(Message message);

    /**
     * 更新聊天室的消息序列号（自增）
     *
     * <p>使用 INSERT ... ON DUPLICATE KEY UPDATE 实现原子自增。
     * 首次插入时 seq_id=1，后续每次 +1。
     *
     * @param chatId 聊天室内部 ID
     */
    void updateChatSequence(@Param("chatId") Integer chatId);

    /**
     * 获取聊天室当前的消息序列号
     *
     * <p>用于发送消息前获取下一条消息的 seq_id。
     * 配合 {@link #updateChatSequence} 使用。
     *
     * @param chatId 聊天室内部 ID
     * @return 当前序列号，首条消息时返回 null
     */
    Long selectCurrentSequence(@Param("chatId") Integer chatId);

    /**
     * 获取用户所有聊天室的未读消息数
     *
     * <p>未读判断逻辑：
     * <ul>
     *   <li>消息发送者不是当前用户</li>
     *   <li>消息创建时间 > 用户在该聊天室的 last_seen_at</li>
     * </ul>
     *
     * @param userId 用户内部 ID
     * @return Map 列表，每项包含 chatCode 和 unreadCount
     */
    @MapKey("chatCode")
    List<Map<String, Object>> selectUnreadCountMapByUserId(Integer userId);

    /**
     * 获取用户在指定聊天室的未读消息数
     *
     * <p>单个聊天室的未读统计，用于进入聊天室时的精确统计。
     *
     * @param userId 用户内部 ID
     * @param chatId 聊天室内部 ID
     * @return 未读消息数
     */
    Integer selectUnreadCountMapByUserIdAndChatId(Integer userId, Integer chatId);

    /**
     * 获取用户所有聊天室的最后一条消息
     *
     * <p>用于聊天室列表展示最后一条消息预览。
     * 使用子查询获取每个聊天室的最大 create_time。
     *
     * @param userId 用户内部 ID
     * @return Map，键为 chatCode，值为 MessageVO
     */
    @MapKey("chatCode")
    Map<String, MessageVO> selectLastMessageListByUserId(Integer userId);

    /**
     * 获取指定聊天室的最后一条消息
     *
     * <p>按 create_time 降序取第一条。
     *
     * @param chatId 聊天室内部 ID
     * @return 消息视图对象，无消息返回 null
     */
    MessageVO selectLastMessageByChatId(Integer chatId);

    /**
     * 获取指定聊天室的消息列表（游标分页）
     *
     * <p>基于 seq_id 的向前分页：
     * <ul>
     *   <li>cursorSeqId 为空时返回最新 30 条</li>
     *   <li>cursorSeqId 有值时返回 seq_id < cursorSeqId 的 30 条</li>
     * </ul>
     *
     * @param chatId      聊天室内部 ID
     * @param cursorSeqId 游标序列号（可选，null 表示从最新开始）
     * @return 消息视图对象列表（按 seq_id 降序）
     */
    List<MessageVO> selectMessageListByChatIdAndCursor(@Param("chatId") Integer chatId,
            @Param("cursorSeqId") Long cursorSeqId);

    /**
     * 获取指定聊天室中序列号大于 lastSeqId 的消息列表
     *
     * <p>用于 WebSocket 重连后的消息同步，获取断线期间的新消息。
     *
     * @param chatId    聊天室内部 ID
     * @param lastSeqId 上次同步的最后序列号
     * @return 新消息列表（按 seq_id 降序）
     */
    List<MessageVO> selectMessagesAfterSeqId(@Param("chatId") Integer chatId, @Param("lastSeqId") Long lastSeqId);

    /**
     * 删除聊天室全部消息
     *
     * <p>解散聊天室时调用，需在删除聊天室记录之前执行。
     *
     * @param chatId 聊天室内部 ID
     */
    void deleteMessagesByChatId(@Param("chatId") Integer chatId);

    /**
     * 删除聊天室序列号记录
     *
     * <p>解散聊天室时调用，清理 chat_sequences 表。
     *
     * @param chatId 聊天室内部 ID
     */
    void deleteChatSequence(@Param("chatId") Integer chatId);

}
