package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作日志实体类
 * <p>
 * 用于记录用户的敏感操作，如创建、删除等。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationLog {
    private Long id;
    private Integer userId;
    private String module;
    private String type;
    private String content;
    private String ip;
    private LocalDateTime createTime;
}
