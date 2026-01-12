package hal.th50743.mapper;

import hal.th50743.pojo.ChatInvite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 邀请码数据访问层
 *
 * <p>负责聊天室邀请码（chat_invites 表）的数据库操作。
 *
 * <h3>主要功能</h3>
 * <ul>
 *   <li>邀请码 CRUD 操作（创建、查询、撤销、删除）</li>
 *   <li>邀请码消费（原子性校验+计数）</li>
 *   <li>有效邀请码查询</li>
 * </ul>
 *
 * <h3>邀请码有效性条件</h3>
 * <ul>
 *   <li>revoked = 0（未撤销）</li>
 *   <li>expires_at > NOW()（未过期）</li>
 *   <li>max_uses = 0 或 used_count < max_uses（次数未用尽）</li>
 * </ul>
 *
 * <h3>表依赖</h3>
 * <ul>
 *   <li>{@code chat_invites} - 邀请码主表</li>
 *   <li>{@code chats} - 聊天室表（关联查询）</li>
 * </ul>
 *
 * <p>注意：本工程不使用物理外键，chat_id 关联关系由业务逻辑保证。
 *
 * @see ChatInvite
 */
@Mapper
public interface ChatInviteMapper {

    /**
     * 插入邀请码记录
     *
     * <p>插入后通过 useGeneratedKeys 回填 id。
     * 初始 used_count=0，revoked=0。
     *
     * @param invite 邀请码实体（包含 chatId、inviteCode、codeHash、expiresAt、maxUses 等）
     * @return 影响行数
     */
    int insertChatInvite(ChatInvite invite);

    /**
     * 消费邀请码
     *
     * <p>原子操作：在单条 SQL 中完成有效性校验和 used_count 自增。
     * 校验条件：未撤销、未过期、次数未用尽。
     * 高并发场景下保证数据一致性。
     *
     * @param chatId   聊天室内部 ID
     * @param codeHash 邀请码 SHA-256 哈希
     * @return 更新行数（1=成功消费，0=无效/已过期/已撤销/次数用尽）
     */
    int consume(@Param("chatId") Integer chatId, @Param("codeHash") String codeHash);

    /**
     * 根据邀请码哈希查找邀请码信息
     *
     * <p>使用 code_hash 索引。返回完整邀请码信息。
     *
     * @param codeHash 邀请码 SHA-256 哈希
     * @return 邀请码实体，不存在返回 null
     */
    ChatInvite selectByCodeHash(@Param("codeHash") String codeHash);

    /**
     * 根据邀请码哈希删除邀请码记录
     *
     * <p>物理删除，通常用于清理过期邀请码。
     *
     * @param codeHash 邀请码 SHA-256 哈希
     * @return 删除的行数
     */
    int deleteByCodeHash(@Param("codeHash") String codeHash);

    /**
     * 根据聊天室ID删除所有邀请码记录
     *
     * <p>解散聊天室时调用，清理所有关联邀请码。
     *
     * @param chatId 聊天室内部 ID
     * @return 删除的行数
     */
    int deleteByChatId(@Param("chatId") Integer chatId);

    /**
     * 查询聊天室下所有有效邀请码
     *
     * <p>有效条件：未撤销、未过期、次数未用尽。
     * 按创建时间降序排列。
     *
     * @param chatId 聊天室内部 ID
     * @return 有效邀请码列表
     */
    java.util.List<ChatInvite> selectActiveInvitesByChatId(@Param("chatId") Integer chatId);

    /**
     * 统计聊天室下有效邀请码数量
     *
     * <p>用于限制每个聊天室的邀请码数量上限。
     *
     * @param chatId 聊天室内部 ID
     * @return 有效邀请码数量
     */
    int countActiveInvitesByChatId(@Param("chatId") Integer chatId);

    /**
     * 撤销邀请码（软删除）
     *
     * <p>设置 revoked=1，不物理删除。
     * 仅撤销指定聊天室的指定邀请码（双重校验）。
     *
     * @param chatId   聊天室内部 ID
     * @param inviteId 邀请码内部 ID
     * @return 更新行数
     */
    int revokeById(@Param("chatId") Integer chatId, @Param("inviteId") Integer inviteId);
}
