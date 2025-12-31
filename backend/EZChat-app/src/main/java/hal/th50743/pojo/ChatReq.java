package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatReq {

    private String chatCode;
    private String chatName;
    private Integer ownerId;
    private Image avatar;
    private Integer joinEnable;
    private Integer joinLinkExpiryMinutes;

}