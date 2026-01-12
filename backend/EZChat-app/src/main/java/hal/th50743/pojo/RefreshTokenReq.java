package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RefreshToken 刷新请求对象
 * <p>
 * 用于客户端请求刷新 AccessToken。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RefreshTokenReq {

    /**
     * RefreshToken（从 localStorage 获取）
     */
    private String refreshToken;
}
