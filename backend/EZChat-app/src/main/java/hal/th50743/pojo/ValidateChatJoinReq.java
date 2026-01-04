package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证聊天室加入请求
 * <p>
 * 支持两种验证模式：
 * <ul>
 *   <li><b>模式1：chatCode + password</b> - 通过房间ID和密码验证（两者必须同时提供）</li>
 *   <li><b>模式2：inviteCode</b> - 通过邀请码验证（可单独使用，当前未实现）</li>
 * </ul>
 * <p>
 * 验证规则：
 * <ul>
 *   <li>如果提供了 chatCode，则 password 必填</li>
 *   <li>如果只提供了 inviteCode，则 chatCode 和 password 不需要</li>
 *   <li>至少需要提供 chatCode 或 inviteCode 之一</li>
 * </ul>
 * <p>
 * 注意：验证逻辑在 Service 层实现，不在 DTO 层使用注解验证
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ValidateChatJoinReq {

    /**
     * 聊天室代码（可选，但提供时必须同时提供 password）
     */
    private String chatCode;

    /**
     * 密码（可选，但提供 chatCode 时必填）
     */
    private String password;

    /**
     * 邀请码（可选，可单独使用进行验证，当前未实现）
     * <p>
     * 预留字段，用于未来扩展邀请链接验证功能
     */
    private String inviteCode;
}

