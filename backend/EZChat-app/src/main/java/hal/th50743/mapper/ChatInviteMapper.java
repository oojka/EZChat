package hal.th50743.mapper;

import hal.th50743.pojo.ChatInvite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 邀请码 Mapper（chat_invites）
 *
 * 注意：本工程不使用物理外键，chat_code 关联由业务逻辑保证。
 */
@Mapper
public interface ChatInviteMapper {

    /**
     * 新增邀请码记录
     *
     * @param invite 邀请码实体
     * @return 影响行数
     */
    int insert(ChatInvite invite);

    /**
     * “消费”邀请码：满足有效条件时 used_count + 1
     *
     * 业务目的：
     * - 在高并发下保证 max_uses / expires_at 校验与计数递增的原子性
     *
     * @param chatCode  房间号
     * @param codeHash  邀请码哈希
     * @return 更新行数（1=成功消费；0=无效/已过期/已撤销/次数用尽）
     */
    int consume(@Param("chatCode") String chatCode, @Param("codeHash") String codeHash);
}


