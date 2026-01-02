package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天成员轻量信息（用于初始化与计数聚合）
 *
 * 业务目的：
 * - refresh 初始化时避免全量加载成员昵称/头像等大字段
 * - 用最小字段完成：用户状态表（在线/离线）与每个房间在线人数统计
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMemberLite {

    /**
     * 聊天室对外 ID（chatCode）
     */
    private String chatCode;

    /**
     * 成员用户内部 ID（users.id）
     */
    private Integer userId;

    /**
     * 成员用户对外 ID（users.uid）
     */
    private String uid;

    /**
     * 最近活跃/最后在线时间（用于前端展示离线时间）
     */
    private LocalDateTime lastSeenAt;
}


