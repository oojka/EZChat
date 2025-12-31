package hal.th50743.pojo;

import io.minio.MinioOSSResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Image {

    private String objectName;
    private String objectUrl;
    private String objectThumbUrl;

}
