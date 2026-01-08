package hal.th50743.mapper;

import hal.th50743.pojo.ChatInvite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 邀请码 Mapper（chat_invites）
 *
 * 注意：本工程不使用物理外键，chat_id 关联由业务逻辑保证。
 */
@Mapper
public interface ChatInviteMapper {

    /**
     * 插入邀请码记录
     *
     * @param invite 邀请码实体
     * @return 影响行数
     */
    int insertChatInvite(ChatInvite invite);

    /**
     * "消费"邀请码：满足有效条件时 used_count + 1
     *
     * 业务目的：
     * - 在高并发下保证 max_uses / expires_at 校验与计数递增的原子性
     *
     * @param chatId   聊天室ID
     * @param codeHash 邀请码哈希
     * @return 更新行数（1=成功消费；0=无效/已过期/已撤销/次数用尽）
     */
    int consume(@Param("chatId") Integer chatId, @Param("codeHash") String codeHash);

    /**
     * 根据邀请码哈希查找邀请码信息
     *
     * @param codeHash 邀请码哈希
     * @return 邀请码信息（包含聊天室信息）
     */
    ChatInvite selectByCodeHash(@Param("codeHash") String codeHash);

    /**
     * 根据邀请码哈希删除邀请码记录
     *
     * @param codeHash 邀请码哈希
     * @return 删除的行数
     */
    int deleteByCodeHash(@Param("codeHash") String codeHash);
}
