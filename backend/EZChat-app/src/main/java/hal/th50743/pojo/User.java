package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {

    private Integer id;
    private String uid;
    private String nickname;
    private String avatarObject;
    private String bio;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 临时用户属性
    private String username;

}
