package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RefreshToken 兑换请求对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RefreshTokenReq {

    /**
     * RefreshToken
     */
    private String refreshToken;
}
