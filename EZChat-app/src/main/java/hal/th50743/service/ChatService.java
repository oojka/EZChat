package hal.th50743.service;

import hal.th50743.pojo.AppInitVO;
import hal.th50743.pojo.Chat;
import hal.th50743.pojo.ChatVO;
import hal.th50743.pojo.JoinChatReq;

import java.util.List;

/**
 * 聊天服务接口
 * <p>
 * 定义聊天室管理、成员查询及初始化数据获取的业务逻辑。
 */
public interface ChatService {

    /**
     * 获取用户的聊天成员ID列表
     *
     * @param userId 用户ID
     * @return 成员ID列表
     */
    List<Integer> getChatMembers(Integer userId);

    /**
     * 获取用户的聊天列表及成员在线状态
     *
     * @param userId 用户ID
     * @return 初始化数据视图对象
     */
    AppInitVO getChatVOListAndMemberStatusList(Integer userId);

    /**
     * 加入聊天室
     *
     * @param joinChatReq 加入请求参数
     * @return 聊天室对象
     */
    Chat join(JoinChatReq joinChatReq);

    /**
     * 获取聊天室详情
     *
     * @param userId   用户ID
     * @param chatCode 聊天室代码
     * @return 聊天室视图对象
     */
    ChatVO getChat(Integer userId, String chatCode);

    /**
     * 根据 ChatCode 获取 ChatId
     *
     * @param userId   用户ID
     * @param chatCode 聊天室代码
     * @return ChatId
     */
    Integer getChatId(Integer userId, String chatCode);

}
