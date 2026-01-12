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

    /**
     * 图片文件名（MinIO 对象名）
     */
    private String imageName;

    /**
     * 图片完整 URL（原图）
     */
    private String imageUrl;

    /**
     * 缩略图 URL（可选，仅大图片生成）
     */
    private String imageThumbUrl;

    /**
     * 对象ID（逻辑外键，关联 assets.id）
     * <p>
     * 可选字段，用于后端直接关联 assets 表，避免根据 imageName 查询
     */
    private Integer assetId;

}
