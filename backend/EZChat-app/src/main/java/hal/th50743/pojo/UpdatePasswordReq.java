package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求对象
 * <p>
 * 用于接收前端修改密码的请求参数。
 * 仅限正式用户使用。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdatePasswordReq {

    /**
     * 旧密码（必填）
     */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /**
     * 新密码（必填，8-20位）
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度必须在8-20位之间")
    private String newPassword;
}
