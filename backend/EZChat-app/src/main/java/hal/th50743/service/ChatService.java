package hal.th50743.service;

import hal.th50743.pojo.AppInitVO;
import hal.th50743.pojo.Chat;
import hal.th50743.pojo.ChatJoinInfo;
import hal.th50743.pojo.ChatMemberVO;
import hal.th50743.pojo.ChatReq;
import hal.th50743.pojo.CreateChatVO;
import hal.th50743.pojo.ChatVO;
import hal.th50743.pojo.JoinChatReq;
import hal.th50743.pojo.ValidateChatJoinReq;
import hal.th50743.exception.BusinessException;

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
     * 获取用户的聊天列表及成员在线状态（轻量版）
     *
     * 业务目的：
     * - refresh 初始化优先渲染 chatList（AsideList）
     * - 不返回 chatMembers，避免初始化阶段数据量过大与头像预取过慢
     *
     * @param userId 用户ID
     * @return 初始化数据视图对象（chatList + userStatusList）
     */
    AppInitVO getChatVOListAndMemberStatusListLite(Integer userId);

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
     * 获取聊天室成员列表（按 chatCode 懒加载）
     *
     * 业务目的：
     * - 右侧成员栏按需拉取成员列表，避免 refresh 初始化阶段全量加载所有群成员
     *
     * @param userId   当前用户内部 ID
     * @param chatCode 聊天室对外 ID
     * @return 成员列表（VO）
     */
    List<ChatMemberVO> getChatMemberVOList(Integer userId, String chatCode);

    /**
     * 根据 ChatCode 获取 ChatId
     *
     * @param userId   用户ID
     * @param chatCode 聊天室代码
     * @return ChatId
     */
    Integer getChatId(Integer userId, String chatCode);

    /**
     * 获取聊天室“加入校验”所需的最小信息（不做成员权限校验）
     *
     * 业务目的：
     * - 访客加入/邀请码加入需要先校验 join_enabled / chat_password_hash
     * - 该方法不要求请求方已经是成员（否则无法加入）
     *
     * @param chatId 聊天对内 ID
     * @return ChatJoinInfo（不存在则返回 null）
     */
    ChatJoinInfo getJoinInfo(Integer chatId);

    /**
     * 创建聊天室
     *
     * 业务目的：
     * - 创建 chats 记录并让创建者自动入群
     * - 生成短邀请码（用于 7 天有效期的邀请链接，免密加入）
     *
     * @param userId  当前用户内部 ID（创建者）
     * @param chatReq 创建请求参数
     * @return 创建结果（chatCode + inviteCode）
     */
    CreateChatVO createChat(Integer userId, ChatReq chatReq);

    /**
     * 验证聊天室加入请求
     * <p>
     * 业务目的：
     * - 轻量级验证接口，仅验证房间是否存在、密码是否正确、是否允许加入
     * - 不执行实际的加入操作（不创建用户、不添加成员）
     * - 用于前端在用户提交加入表单前进行预验证
     * <p>
     * 支持两种验证模式：
     * <ul>
     * <li><b>模式1：chatCode + password</b> - 通过房间ID和密码验证（两者必须同时提供）</li>
     * <li><b>模式2：inviteCode</b> - 通过邀请码验证（可单独使用，当前未实现）</li>
     * </ul>
     * <p>
     * 验证逻辑：
     * <ol>
     * <li>检查房间是否存在（42001）</li>
     * <li>检查是否允许加入（joinEnabled == 1，否则返回 40300）</li>
     * <li>检查密码登录是否启用（password_hash 是否为 null，为 null 则返回 40300）</li>
     * <li>验证密码是否正确（42004）</li>
     * </ol>
     *
     * @param req 验证请求对象（包含 chatCode + password 或 inviteCode）
     * @return 简化的 ChatVO（仅包含 chatCode, chatName, avatar, memberCount，其他字段为 null）
     * @throws BusinessException 如果验证失败（房间不存在、禁止加入、密码错误等）
     */
    ChatVO validateChatJoin(ValidateChatJoinReq req);

}
