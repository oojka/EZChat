package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatReq {

    private String chatCode;
    private String chatName;
    private Integer ownerId;
    private Image avatar;
    private Integer joinEnable;
    private Integer joinLinkExpiryMinutes;
    /**
     * 加入密码（可选）
     *
     * 业务规则：
     * - 为空：表示不启用密码（DB chat_password_hash = NULL）
     * - 非空：后端会做 BCrypt 哈希并写入 chat_password_hash
     */
    private String password;
    /**
     * 密码确认（可选）
     *
     * 业务目的：前端已校验，但后端仍做一次一致性校验，避免绕过。
     */
    private String passwordConfirm;
    /**
     * 邀请链接最大使用次数（可选）
     *
     * 业务规则：
     * - null 或 0：无限使用（直到过期）
     * - 1：一次性链接（使用一次后即失效）
     * - 暂不考虑 >1 的情况
     */
    private Integer maxUses;

}