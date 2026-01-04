package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 创建聊天室请求对象
 * <p>
 * 用于接收前端创建聊天室的请求参数。
 * 包含聊天室基本信息、安全设置及邀请链接配置。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatReq {

    /**
     * 聊天室代码（8位数字，可选）
     * <p>
     * 业务规则：
     * - 如果提供：使用指定的代码（需确保唯一性）
     * - 如果为空：后端自动生成8位数字代码
     */
    private String chatCode;

    /**
     * 聊天室名称（必填）
     */
    private String chatName;

    /**
     * 所有者用户内部ID（从Token中获取，前端不需要传递）
     */
    private Integer ownerId;

    /**
     * 聊天室头像（可选）
     * <p>
     * 业务规则：
     * - 如果提供：使用指定的头像
     * - 如果为空：使用默认头像
     */
    private Image avatar;

    /**
     * 是否允许加入（可选，默认1）
     * <p>
     * 业务规则：
     * - 1: 允许加入（默认）
     * - 0: 禁止加入（创建即关闭）
     */
    private Integer joinEnable;

    /**
     * 邀请链接过期时间（分钟，可选）
     * <p>
     * 业务规则：
     * - 如果提供：邀请链接在指定分钟后过期
     * - 如果为空：使用默认过期时间（如7天）
     */
    private Integer joinLinkExpiryMinutes;

    /**
     * 加入密码（可选）
     * <p>
     * 业务规则：
     * - 为空：表示不启用密码（DB chat_password_hash = NULL）
     * - 非空：后端会做 BCrypt 哈希并写入 chat_password_hash
     */
    private String password;

    /**
     * 密码确认（可选）
     * <p>
     * 业务目的：前端已校验，但后端仍做一次一致性校验，避免绕过。
     */
    private String passwordConfirm;

    /**
     * 邀请链接最大使用次数（可选）
     * <p>
     * 业务规则：
     * - null 或 0：无限使用（直到过期）
     * - 1：一次性链接（使用一次后即失效）
     * - 暂不考虑 >1 的情况
     */
    private Integer maxUses;

}