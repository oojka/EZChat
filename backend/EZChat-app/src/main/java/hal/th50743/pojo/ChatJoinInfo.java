package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天室“加入校验”所需的最小信息
 *
 * 业务目的：
 * - join / invite join 等逻辑仅需要 chatId、joinEnabled、passwordHash 等字段
 * - 避免为了校验而拉取完整 ChatVO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatJoinInfo {
    private Integer chatId;
    private String chatName;
    private String chatCode;
    private Integer joinEnabled;
    private String chatPasswordHash;
    private Integer maxMembers;
}

