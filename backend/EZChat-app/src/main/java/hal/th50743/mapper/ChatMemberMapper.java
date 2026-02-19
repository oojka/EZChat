package hal.th50743.mapper;

import hal.th50743.pojo.ChatMember;
import hal.th50743.pojo.ChatMemberLite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天室成员数据访问层
 *
 * <p>负责聊天室成员关系（chat_members 表）的数据库操作。
 *
 * <h3>主要功能</h3>
 * <ul>
 *   <li>成员关系 CRUD 操作（加入、退出、删除）</li>
 *   <li>成员列表查询（完整信息、轻量信息）</li>
 *   <li>成员权限校验（是否有权获取用户信息）</li>
 *   <li>最后活跃时间更新（用于未读计算）</li>
 * </ul>
 *
 * <h3>表依赖</h3>
 * <ul>
 *   <li>{@code chat_members} - 成员关系主表</li>
 *   <li>{@code users} - 用户表（成员信息）</li>
 *   <li>{@code chats} - 聊天室表（chatCode 关联）</li>
 *   <li>{@code assets} - 资产表（头像关联）</li>
 * </ul>
 *
 * @see ChatMember
 * @see ChatMemberLite
 */
@Mapper
public interface ChatMemberMapper {

    /**
     * 获取用户关联的所有成员ID列表
     *
     * <p>查询当前用户所在的所有聊天室中的所有成员 ID（去重）。
     * 排除已删除用户（is_deleted=0）。
     *
     * @param id 当前用户内部 ID
     * @return 成员内部 ID 列表（去重）
     */
    List<Integer> selectChatMembersById(Integer id);

    /**
     * 获取指定聊天室的所有成员ID列表
     *
     * <p>包含权限校验：仅当 userId 是该聊天室成员时才返回结果。
     *
     * @param userId 请求用户内部 ID（用于权限校验）
     * @param chatId 聊天室内部 ID
     * @return 成员内部 ID 列表
     */
    List<Integer> selectChatMembersByUserIdAndChatId(Integer userId, Integer chatId);

    /**
     * 获取用户所在所有聊天室的成员详情列表
     *
     * <p>返回完整成员信息，包含 nickname、avatar 等。
     * 用于初始化时一次性获取所有成员数据。
     *
     * @param userId 当前用户内部 ID
     * @return 成员详情列表（包含 chatCode、uid、nickname、avatarAssetName 等）
     */
    List<ChatMember> selectChatMemberListByUserId(Integer userId);

    /**
     * 获取用户所在所有聊天室的轻量成员列表
     *
     * <p>仅返回 userId、uid、lastSeenAt、chatCode，用于：
     * <ul>
     *   <li>初始化阶段聚合在线状态</li>
     *   <li>计算在线人数</li>
     * </ul>
     * 避免携带 nickname/avatar 导致数据量过大。
     *
     * @param userId 当前用户内部 ID
     * @return 轻量成员列表
     */
    List<ChatMemberLite> selectChatMemberLiteListByUserId(Integer userId);

    /**
     * 获取指定聊天室的所有成员详情列表
     *
     * <p>返回完整成员信息，用于聊天室内的成员列表展示。
     * JOIN users 和 assets 表获取完整信息。
     *
     * @param chatId 聊天室内部 ID
     * @return 成员详情列表
     */
    List<ChatMember> selectChatMemberListByChatId(Integer chatId);

    /**
     * 验证用户是否有权获取目标用户的信息
     *
     * <p>校验逻辑：请求者和目标用户是否在同一个聊天室中。
     * 用于用户信息接口的权限控制。
     *
     * @param reqId 请求用户内部 ID
     * @param id    目标用户内部 ID
     * @return 有权返回 true，否则返回 false
     */
    boolean isValidGetInfoReq(Integer reqId, Integer id);

    /**
     * 更新成员在聊天室中的最后活跃时间
     *
     * <p>用户阅读消息时调用，用于未读消息计算。
     *
     * @param userId 用户内部 ID
     * @param chatId 聊天室内部 ID
     * @param now    当前时间
     */
    void updateLastSeenAt(Integer userId, Integer chatId, LocalDateTime now);

    /**
     * 插入聊天室成员关系
     *
     * <p>用于用户加入聊天室（join/guest join/invite join）。
     * 如果已存在则忽略（避免重复加入导致主键冲突）。
     *
     * @param chatId 聊天室内部 ID
     * @param userId 用户内部 ID
     * @param now    当前时间（用于 last_seen_at、create_time、update_time）
     */
    void insertChatMember(@Param("chatId") Integer chatId, @Param("userId") Integer userId,
            @Param("now") LocalDateTime now);

    /**
     * 删除单个成员关系
     *
     * <p>用于用户主动退出聊天室或被群主踢出。
     *
     * @param chatId 聊天室内部 ID
     * @param userId 用户内部 ID
     * @return 影响行数
     */
    int deleteChatMember(@Param("chatId") Integer chatId, @Param("userId") Integer userId);

    /**
     * 删除聊天室全部成员
     *
     * <p>解散聊天室时调用，需在删除聊天室记录之前执行。
     *
     * @param chatId 聊天室内部 ID
     * @return 影响行数
     */
    int deleteChatMembersByChatId(@Param("chatId") Integer chatId);

    /**
     * 批量删除聊天室成员
     *
     * <p>群主批量踢人时使用。
     *
     * @param chatId  聊天室内部 ID
     * @param userIds 需要移除的成员内部 ID 列表
     * @return 影响行数
     */
    int deleteChatMembersByChatIdAndUserIds(@Param("chatId") Integer chatId, @Param("userIds") List<Integer> userIds);

    /**
     * 按用户删除其所有聊天室成员关系
     *
     * <p>用于访客清理或账号清理场景，避免软删除用户后成员统计偏差。
     *
     * @param userId 用户内部 ID
     * @return 影响行数
     */
    int deleteChatMembersByUserId(@Param("userId") Integer userId);

    /**
     * 统计聊天室成员数
     *
     * <p>用于加入校验（判断是否已达人数上限）。
     *
     * @param chatId 聊天室内部 ID
     * @return 成员数量
     */
    int countMembersByChatId(@Param("chatId") Integer chatId);
}
