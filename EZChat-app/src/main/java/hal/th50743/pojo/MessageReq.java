package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageReq {

    private String sender;
    private String chatCode;
    private String text;
    private List<Image> Images;
    private LocalDateTime createTime;
    private String tempId;

}
