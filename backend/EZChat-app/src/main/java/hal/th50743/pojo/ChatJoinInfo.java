package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天室加入校验信息
 * <p>
 * 用于 join / invite join 等逻辑的轻量级校验对象。
 * 避免为校验拉取完整的 ChatVO。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatJoinInfo {

    /**
     * 聊天室内部ID
     */
    private Integer chatId;

    /**
     * 聊天室名称
     */
    private String chatName;

    /**
     * 聊天室对外代码（8位数字）
     */
    private String chatCode;

    /**
     * 是否允许加入（0=禁止, 1=允许）
     */
    private Integer joinEnabled;

    /**
     * 聊天室密码哈希（BCrypt，null 表示无密码）
     */
    private String chatPasswordHash;

    /**
     * 成员上限
     */
    private Integer maxMembers;
}

