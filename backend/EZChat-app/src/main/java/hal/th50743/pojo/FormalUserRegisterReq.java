package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FormalUserRegisterReq {

    private Integer userId; //user表的主键ID
    private String userUId; //关联User表的uId
    private String username;
    private String password;
    private String confirmPassword;
    private String nickname;
    private Image avatar;

}
