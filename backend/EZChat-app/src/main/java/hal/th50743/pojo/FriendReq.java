package hal.th50743.pojo;

import lombok.Data;

/**
 * 好友操作请求对象
 * <p>
 * 统一的好友相关操作请求体，根据不同接口使用不同字段组合：
 * <ul>
 *   <li>发送申请：targetUid</li>
 *   <li>处理申请：requestId + accept</li>
 *   <li>修改备注：friendUid + alias</li>
 *   <li>删除好友：friendUid</li>
 * </ul>
 */
@Data
public class FriendReq {

    /**
     * 目标用户UID（发送好友申请时使用）
     */
    private String targetUid;

    /**
     * 好友申请ID（处理申请时使用）
     */
    private Integer requestId;

    /**
     * 是否接受申请（true=接受, false=拒绝）
     */
    private Boolean accept;

    /**
     * 好友用户UID（修改备注、删除好友时使用）
     */
    private String friendUid;

    /**
     * 好友备注名
     */
    private String alias;
}
