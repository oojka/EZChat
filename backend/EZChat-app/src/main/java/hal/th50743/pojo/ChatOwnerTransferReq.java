package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 群主转让请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatOwnerTransferReq {

    /**
     * 新群主 UID
     */
    private String newOwnerUid;
}
