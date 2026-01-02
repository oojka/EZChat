package hal.th50743.mapper;

import hal.th50743.pojo.ChatMember;
import hal.th50743.pojo.ChatMemberLite;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天室成员 Mapper 接口
 * <p>
 * 负责聊天室成员相关的数据库操作。
 */
@Mapper
public interface ChatMemberMapper {

    /**
     * 获取用户加入的所有聊天室的成员ID列表
     *
     * @param id 用户ID
     * @return 成员ID列表
     */
    List<Integer> getChatMembersById(Integer id);

    /**
     * 获取指定聊天室的所有成员ID列表
     *
     * @param userId 用户ID（用于校验权限）
     * @param chatId 聊天室ID
     * @return 成员ID列表
     */
    List<Integer> getChatMembersByUserIdAndChatId(Integer userId, Integer chatId);

    /**
     * 获取用户加入的所有聊天室的成员详情列表
     *
     * @param userId 用户ID
     * @return 成员详情列表
     */
    List<ChatMember> getChatMemberListByUserId(Integer userId);

    /**
     * 获取“轻量成员列表”（用于初始化聚合在线状态/在线人数）
     *
     * 业务目的：
     * - refresh 初始化阶段只需要 userId/uid/lastSeenAt 与 chatCode
     * - 避免携带 nickname/avatar 等字段导致数据量过大
     *
     * @param userId 当前用户内部 ID（users.id）
     * @return 轻量成员列表
     */
    List<ChatMemberLite> getChatMemberLiteListByUserId(Integer userId);

    /**
     * 获取指定聊天室的所有成员详情列表
     *
     * @param chatId 聊天室ID
     * @return 成员详情列表
     */
    List<ChatMember> getChatMemberListByChatId(Integer chatId);

    /**
     * 验证用户是否有权获取目标用户的信息（是否是好友或群友）
     *
     * @param reqId 请求用户ID
     * @param id    目标用户ID
     * @return 有权返回 true，否则返回 false
     */
    boolean isValidGetInfoReq(Integer reqId, Integer id);

    /**
     * 更新成员在聊天室中的最后活跃时间
     *
     * @param userId 用户ID
     * @param chatId 聊天室ID
     * @param now    当前时间
     */
    void updateLastSeenAt(Integer userId, Integer chatId, LocalDateTime now);
}
