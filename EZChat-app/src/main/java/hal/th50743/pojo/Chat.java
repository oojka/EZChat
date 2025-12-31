package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Chat {

    private Integer id;
    private String chatCode;
    private String chatName;
    private Integer ownerId;
    private String avatarName;
    private Integer joinEnabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
