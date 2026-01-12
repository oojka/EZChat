package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 加入聊天室请求
 * <p>
 * 支持两种验证模式：
 * 1. 密码模式：chatCode + password
 * 2. 邀请码模式：inviteCode
 * <p>
 * 注意：两种模式互斥，前端通过不同的字段组合来区分
 * uid 字段由后端从Token中获取，不需要前端传递
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JoinChatReq {

    /**
     * 模式1：密码验证 - 聊天室代码（8位数字）
     * 与 password 字段强绑定，不能与 inviteCode 同时存在
     */
    private String chatCode;
    
    /**
     * 模式1：密码验证 - 聊天室密码
     * 与 chatCode 字段强绑定
     */
    private String password;
    
    /**
     * 模式2：邀请码验证 - 邀请码（16-24位字符）
     * 不能与 chatCode/password 同时存在
     */
    private String inviteCode;

    /**
     * 用户内部ID（后端从 Token 获取，前端无需传递）
     */
    private Integer userId;

}
