package hal.th50743.service;

import hal.th50743.pojo.ChatInviteCreateReq;
import hal.th50743.pojo.ChatInviteVO;

import java.util.List;

/**
 * 聊天室邀请链接管理服务接口
 * <p>
 * 定义邀请链接的创建、查询、撤销及使用计数等业务逻辑。
 * <p>
 * 邀请链接功能说明：
 * <ul>
 *     <li>每个聊天室最多拥有 5 个有效邀请链接</li>
 *     <li>邀请链接支持设置有效期（分钟）和最大使用次数</li>
 *     <li>一次性链接（maxUses=1）使用后自动失效</li>
 *     <li>仅群主可以管理邀请链接</li>
 * </ul>
 */
public interface ChatInviteService {

    /**
     * 获取聊天室的有效邀请链接列表
     * <p>
     * 返回指定聊天室所有未过期、未撤销、未达到使用上限的邀请链接。
     *
     * @param userId   当前用户ID（需验证群主权限）
     * @param chatCode 聊天室代码
     * @return 有效邀请链接列表
     */
    List<ChatInviteVO> listActiveInvites(Integer userId, String chatCode);

    /**
     * 创建新的邀请链接
     * <p>
     * 为指定聊天室创建新的邀请链接。
     * 注意：每个聊天室最多 5 个有效链接，超出时需先撤销旧链接。
     *
     * @param userId   当前用户ID（需验证群主权限）
     * @param chatCode 聊天室代码
     * @param req      创建请求（包含有效期、最大使用次数等）
     * @return 新创建的邀请链接信息
     */
    ChatInviteVO createInvite(Integer userId, String chatCode, ChatInviteCreateReq req);

    /**
     * 撤销邀请链接
     * <p>
     * 将指定邀请链接标记为已撤销，使其立即失效。
     *
     * @param userId   当前用户ID（需验证群主权限）
     * @param chatCode 聊天室代码
     * @param inviteId 邀请链接ID
     */
    void revokeInvite(Integer userId, String chatCode, Integer inviteId);

    /**
     * 为指定聊天室ID创建邀请链接（内部方法）
     * <p>
     * 供内部业务调用，跳过 chatCode 到 chatId 的转换。
     *
     * @param userId        当前用户ID
     * @param chatId        聊天室内部ID
     * @param expiryMinutes 有效期（分钟），null 表示使用默认值
     * @param maxUses       最大使用次数，null 表示无限制
     * @return 新创建的邀请链接信息
     */
    ChatInviteVO createInviteForChatId(Integer userId, Integer chatId, Integer expiryMinutes, Integer maxUses);

    /**
     * 消费邀请码（增加使用次数）
     * <p>
     * 当用户通过邀请码成功加入聊天室时调用，将 used_count 加 1。
     * 如果使用次数达到 max_uses，邀请码将自动失效。
     *
     * @param chatId   聊天室内部ID
     * @param codeHash 邀请码哈希值
     */
    void consumeInvite(Integer chatId, String codeHash);
}
