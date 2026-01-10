package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天室基础信息更新请求
 * <p>
 * 用于群主更新头像、群名、人数上限与群公告。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatBasicUpdateReq {

    /**
     * 群名
     */
    private String chatName;

    /**
     * 群成员上限（2 ~ 200）
     */
    private Integer maxMembers;

    /**
     * 群公告（最多 500 字符）
     */
    private String announcement;

    /**
     * 群头像
     */
    private Image avatar;
}
