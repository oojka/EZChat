package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片对象
 * <p>
 * 用于前后端交互，包含图片的完整信息。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Image {

    private String imageName;
    private String imageUrl;
    private String imageThumbUrl;
    /**
     * 对象 ID，用于直接关联 objects 表
     * <p>
     * 业务说明：
     * - 可选字段，向后兼容（旧版本前端可能不包含此字段）
     * - 如果提供，后端可以直接使用 objectId 关联 objects 表，无需根据 objectName 查询
     * - 性能优化：避免频繁查询 objects 表
     */
    private Integer assetId;

}
