package hal.th50743.mapper;

import hal.th50743.pojo.ChatCreate;
import hal.th50743.pojo.ChatJoinInfo;
import hal.th50743.pojo.ChatVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 聊天室 Mapper 接口
 * <p>
 * 负责聊天室相关的数据库操作。
 */
@Mapper
public interface ChatMapper {

    /**
     * 根据聊天室代码获取聊天室ID
     *
     * @param chatCode 聊天室代码
     * @return 聊天室ID
     */
    Integer selectChatIdByChatCode(String chatCode);

    /**
     * 获取用户加入的聊天室列表
     *
     * @param userId 用户ID
     * @return 聊天室视图对象列表
     */
    List<ChatVO> selectChatVOListByUserId(Integer userId);

    /**
     * 验证用户是否是聊天室成员
     *
     * @param userId 用户ID
     * @param chatId 聊天室ID
     * @return 是成员返回 true，否则返回 false
     */
    boolean isValidChatId(Integer userId, Integer chatId);

    /**
     * 根据聊天室ID获取聊天室详情
     *
     * @param chatId 聊天室ID
     * @return 聊天室视图对象
     */
    ChatVO selectChatVOByChatId(Integer chatId);

    /**
     * 获取聊天室头像资源ID
     *
     * @param chatId 聊天室ID
     * @return 头像资源ID
     */
    Integer selectAssetIdByChatId(Integer chatId);

    /**
     * 获取"加入校验"所需的最小信息（根据聊天室 ID）
     *
     * @param chatId 聊天内部 ID
     * @return ChatJoinInfo（包含 joinEnabled / chatPasswordHash / chatId）
     */
    ChatJoinInfo selectJoinInfoByChatId(Integer chatId);

    /**
     * 插入聊天室
     * <p>
     * 注意：本工程不使用物理外键，owner_id 关联关系由业务逻辑保证。
     *
     * @param chat 创建聊天室写入对象
     */
    void insertChat(ChatCreate chat);

    /**
     * 根据邀请码哈希获取聊天室ID
     *
     * @param codeHash 邀请码哈希
     * @return 聊天室ID
     */
    Integer selectChatIdByInviteCodeHash(String codeHash);

    /**
     * 获取聊天室群主ID
     *
     * @param chatId 聊天室ID
     * @return 群主用户ID
     */
    Integer selectOwnerIdByChatId(Integer chatId);

    /**
     * 更新聊天室群主
     *
     * @param chatId  聊天室ID
     * @param ownerId 新群主用户ID
     */
    void updateChatOwner(Integer chatId, Integer ownerId);

    /**
     * 更新聊天室密码哈希
     *
     * @param chatId 聊天室ID
     * @param passwordHash 密码哈希（null 表示关闭密码）
     */
    void updateChatPassword(Integer chatId, String passwordHash);

    /**
     * 更新聊天室基础信息（群名、人数上限、公告、头像）
     *
     * @param chatId 聊天室ID
     * @param chatName 群名
     * @param maxMembers 群成员上限
     * @param announcement 群公告
     * @param assetId 头像资源ID
     */
    void updateChatBasicInfo(@Param("chatId") Integer chatId,
                             @Param("chatName") String chatName,
                             @Param("maxMembers") Integer maxMembers,
                             @Param("announcement") String announcement,
                             @Param("assetId") Integer assetId);

    /**
     * 删除聊天室
     *
     * @param chatId 聊天室ID
     */
    void deleteChatById(Integer chatId);
}
