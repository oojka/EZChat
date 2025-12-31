package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息实体类
 * <p>
 * 对应数据库中的消息表。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Message {

    private Integer id;
    private Integer senderId;
    private Integer chatId;
    private String text;
    
    /**
     * 存储附件对象名的 JSON 字符串
     */
    private String objectNames;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
