package hal.th50743.mapper;

import hal.th50743.pojo.ChatCreate;
import hal.th50743.pojo.ChatJoinInfo;
import hal.th50743.pojo.ChatVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 聊天室数据访问层
 *
 * <p>提供聊天室相关的数据库操作，包括群聊和私聊的管理。
 *
 * <h3>主要功能</h3>
 * <ul>
 *   <li>聊天室 CRUD 操作（创建、查询、更新、删除）</li>
 *   <li>成员关系验证（isValidChatId）</li>
 *   <li>群主权限管理（转让、密码设置）</li>
 *   <li>邀请码关联查询</li>
 *   <li>私聊房间查询</li>
 * </ul>
 *
 * <h3>表依赖</h3>
 * <ul>
 *   <li>{@code chats} - 聊天室主表</li>
 *   <li>{@code chat_members} - 成员关系表（用于权限校验）</li>
 *   <li>{@code chat_invites} - 邀请码表（关联查询）</li>
 *   <li>{@code assets} - 资产表（头像关联）</li>
 * </ul>
 *
 * @see ChatVO
 * @see ChatCreate
 * @see ChatJoinInfo
 */
@Mapper
public interface ChatMapper {

    /**
     * 根据聊天室代码获取聊天室ID
     *
     * @param chatCode 8 位数字房间号（对外唯一标识）
     * @return 聊天室内部 ID，不存在返回 null
     */
    Integer selectChatIdByChatCode(String chatCode);

    /**
     * 获取用户加入的聊天室列表（仅群聊）
     *
     * <p>业务说明：
     * <ul>
     *   <li>只返回 type=0 的群聊</li>
     *   <li>私聊(type=1)通过好友系统入口展示</li>
     *   <li>包含成员数量统计（子查询）</li>
     * </ul>
     *
     * @param userId 用户内部 ID
     * @return 聊天室视图对象列表
     */
    List<ChatVO> selectChatVOListByUserId(Integer userId);

    /**
     * 验证用户是否是聊天室成员
     *
     * <p>用于发送消息、获取成员列表等操作前的权限校验。
     *
     * @param userId 用户内部 ID
     * @param chatId 聊天室内部 ID
     * @return 是成员返回 true，否则返回 false
     */
    boolean isValidChatId(Integer userId, Integer chatId);

    /**
     * 根据聊天室ID获取聊天室详情
     *
     * <p>返回完整的聊天室信息，包括群主、头像、成员数等。
     *
     * @param chatId 聊天室内部 ID
     * @return 聊天室视图对象，不存在返回 null
     */
    ChatVO selectChatVOByChatId(Integer chatId);

    /**
     * 获取聊天室头像资源ID
     *
     * <p>用于更新头像时判断是否需要删除旧头像。
     *
     * @param chatId 聊天室内部 ID
     * @return 头像资源 ID，无头像返回 null
     */
    Integer selectAssetIdByChatId(Integer chatId);

    /**
     * 获取"加入校验"所需的最小信息
     *
     * <p>用于加入聊天室时的前置校验，避免查询完整信息。
     * 包含：joinEnabled、chatPasswordHash、maxMembers 等。
     *
     * @param chatId 聊天室内部 ID
     * @return ChatJoinInfo 对象，不存在返回 null
     */
    ChatJoinInfo selectJoinInfoByChatId(Integer chatId);

    /**
     * 插入聊天室记录
     *
     * <p>注意：本工程不使用物理外键，owner_id 关联关系由业务逻辑保证。
     * 插入后通过 useGeneratedKeys 回填 id。
     *
     * @param chat 创建聊天室写入对象（包含 chatCode、chatName、ownerId 等）
     */
    void insertChat(ChatCreate chat);

    /**
     * 根据邀请码哈希获取聊天室ID
     *
     * <p>用于邀请链接加入时的聊天室定位。
     *
     * @param codeHash 邀请码 SHA-256 哈希
     * @return 聊天室内部 ID，不存在返回 null
     */
    Integer selectChatIdByInviteCodeHash(String codeHash);

    /**
     * 获取聊天室群主ID
     *
     * <p>用于群主权限校验（如解散群、转让群主）。
     *
     * @param chatId 聊天室内部 ID
     * @return 群主用户内部 ID
     */
    Integer selectOwnerIdByChatId(Integer chatId);

    /**
     * 更新聊天室群主
     *
     * <p>用于群主转让功能。同时更新 update_time。
     *
     * @param chatId  聊天室内部 ID
     * @param ownerId 新群主用户内部 ID
     */
    void updateChatOwner(Integer chatId, Integer ownerId);

    /**
     * 更新聊天室密码哈希
     *
     * <p>用于设置/修改/关闭群密码。传入 null 表示关闭密码。
     *
     * @param chatId       聊天室内部 ID
     * @param passwordHash 密码 BCrypt 哈希，null 表示关闭密码
     */
    void updateChatPassword(Integer chatId, String passwordHash);

    /**
     * 更新聊天室基础信息
     *
     * <p>一次性更新群名、人数上限、公告、头像，避免多次 SQL。
     *
     * @param chatId       聊天室内部 ID
     * @param chatName     群名称
     * @param maxMembers   群成员上限（私聊固定为 2）
     * @param announcement 群公告（可为 null）
     * @param assetId      头像资源 ID（可为 null）
     */
    void updateChatBasicInfo(@Param("chatId") Integer chatId,
                             @Param("chatName") String chatName,
                             @Param("maxMembers") Integer maxMembers,
                             @Param("announcement") String announcement,
                             @Param("assetId") Integer assetId);

    /**
     * 删除聊天室
     *
     * <p>物理删除聊天室记录。调用前需先删除关联的成员、消息、邀请码等。
     *
     * @param chatId 聊天室内部 ID
     */
    void deleteChatById(Integer chatId);

    /**
     * 获取两个用户之间的私聊房间代码
     *
     * <p>用于好友发起私聊时检查是否已存在私聊房间。
     * 查询条件：type=1（私聊）且双方都是成员。
     *
     * @param u1 用户1的内部 ID
     * @param u2 用户2的内部 ID
     * @return 私聊房间 chatCode，不存在返回 null
     */
    String selectPrivateChatCodeBetweenUsers(@Param("u1") Integer u1, @Param("u2") Integer u2);
}
