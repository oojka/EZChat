package hal.th50743.service;

import hal.th50743.pojo.ChatInviteCreateReq;
import hal.th50743.pojo.ChatInviteVO;

import java.util.List;

/**
 * 邀请链接管理服务接口
 * <p>
 * 仅群主可操作，用于创建、查询、撤销邀请链接。
 */
public interface ChatInviteService {

    /**
     * 查询聊天室有效邀请链接列表
     *
     * @param userId 当前用户ID
     * @param chatCode 聊天室代码
     * @return 有效邀请链接列表
     */
    List<ChatInviteVO> listActiveInvites(Integer userId, String chatCode);

    /**
     * 创建新的邀请链接
     *
     * @param userId 当前用户ID
     * @param chatCode 聊天室代码
     * @param req 创建请求
     * @return 新创建的邀请链接信息
     */
    ChatInviteVO createInvite(Integer userId, String chatCode, ChatInviteCreateReq req);

    /**
     * 撤销邀请链接
     *
     * @param userId 当前用户ID
     * @param chatCode 聊天室代码
     * @param inviteId 邀请码ID
     */
    void revokeInvite(Integer userId, String chatCode, Integer inviteId);

    /**
     * 创建邀请链接（内部复用）
     * <p>
     * 仅用于创建聊天室后生成首个邀请链接。
     *
     * @param userId 创建者用户ID
     * @param chatId 聊天室ID
     * @param expiryMinutes 过期分钟数
     * @param maxUses 最大使用次数
     * @return 新创建的邀请链接信息
     */
    ChatInviteVO createInviteForChatId(Integer userId, Integer chatId, Integer expiryMinutes, Integer maxUses);
}
