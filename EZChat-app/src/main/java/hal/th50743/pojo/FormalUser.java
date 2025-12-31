package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FormalUser {

    private Integer userId;  //user表的主键ID
    private String username;
    private String passwordHash;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime lastLoginTime;

    //连表查询User表的uId
    private String userUId;

}
