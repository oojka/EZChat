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
    private Integer objectId;
    /**
     * 临时字段，用于 JOIN 查询获取 object_name
     * <p>
     * 业务说明：通过 LEFT JOIN objects 表获取头像对象名，用于构建 Image 对象
     */
    private transient String avatarObjectName;
    private Integer joinEnabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
