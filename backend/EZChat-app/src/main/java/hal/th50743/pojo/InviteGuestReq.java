package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邀请码免密加入（访客）请求
 *
 * 业务目的：
 * - 用短邀请码（inviteCode）换取“入群权限”，并创建临时用户返回 JWT
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InviteGuestReq {
    private String chatCode;
    private String inviteCode;
    private String nickname;
}


