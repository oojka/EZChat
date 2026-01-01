package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageVO {

    private String sender;
    private String chatCode;
    /**
     * 0: Text, 1: Image, 2: Mixed
     */
    private Integer type;
    private String text;
    private String objectNames;
    private List<Image> images;
    private LocalDateTime createTime;

}