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
    /**
     * 群内消息序号
     */
    private Long seqId;
    /**
     * 0: Text, 1: Image, 2: Mixed
     */
    private Integer type;
    private String text;

    /**
     * 存储图片对象ID列表的 JSON 字符串（格式：JSON 数组，如 [1,2,3]）
     */
    private String assetIds;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
