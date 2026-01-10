package hal.th50743.pojo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量移除成员请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatKickReq {

    /**
     * 被移除的成员 UID 列表
     */
    private List<String> memberUids;
}
