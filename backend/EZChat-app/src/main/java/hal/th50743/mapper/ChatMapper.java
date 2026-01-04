package hal.th50743.mapper;

import hal.th50743.pojo.ChatCreate;
import hal.th50743.pojo.ChatJoinInfo;
import hal.th50743.pojo.ChatVO;
import org.apache.ibatis.annotations.Mapper;

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
    Integer getChatIdByChatCode(String chatCode);

    /**
     * 获取用户加入的聊天室列表
     *
     * @param userId 用户ID
     * @return 聊天室视图对象列表
     */
    List<ChatVO> getChatVOListByUserId(Integer userId);

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
    ChatVO getChatVOByChatId(Integer chatId);

    /**
     * 获取“加入校验”所需的最小信息（根据聊天室 ID）
     *
     * @param chatId 聊天内部 ID
     * @return ChatJoinInfo（包含 joinEnabled / chatPasswordHash / chatId）
     */
    ChatJoinInfo getJoinInfoByChatId(Integer chatId);


    /**
     * 新增聊天室
     *
     * 注意：本工程不使用物理外键，owner_id 关联关系由业务逻辑保证。
     *
     * @param chat 创建聊天室写入对象
     * @return 影响行数
     */
    int insertChat(ChatCreate chat);
}
