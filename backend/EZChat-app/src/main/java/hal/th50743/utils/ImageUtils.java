package hal.th50743.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hal.th50743.pojo.Image;
import io.minio.MinioOSSOperator;
import io.minio.MinioOSSResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 图像处理工具类
 * <p>
 * 负责图像对象名的反序列化及 URL 生成。
 */
@Slf4j
public final class ImageUtils {

    private ImageUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 从存储在数据库中的 JSON 字符串构建 Image 对象列表。
     *
     * @param objectNamesJson  包含对象名列表的 JSON 字符串
     * @param objectMapper     用于反序列化的 ObjectMapper 实例
     * @param minioOperator    用于生成 URL 的 MinioOSSOperator 实例
     * @return 包含完整 URL 的 Image 对象列表
     */
    public static List<Image> buildImagesFromJson(String objectNamesJson, ObjectMapper objectMapper, MinioOSSOperator minioOperator) {
        if (objectNamesJson == null || objectNamesJson.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // 1. 将 JSON 字符串反序列化为对象名列表
            List<String> objectNames = objectMapper.readValue(objectNamesJson, new TypeReference<List<String>>() {});

            // 2. 使用 getImageUrls() 方法获取图片 URL（自动处理 public/private 分流）
            // 有效期设置为 30 分钟，与之前保持一致
            return objectNames.stream()
                    .map(name -> {
                        MinioOSSResult result = minioOperator.getImageUrls(name, 30, TimeUnit.MINUTES);
                        return new Image(result.getObjectName(), result.getUrl(), result.getThumbUrl());
                    })
                    .collect(Collectors.toList());

        } catch (JsonProcessingException e) {
            log.error("反序列化对象名称JSON失败: {}", objectNamesJson, e);
            // 抛出运行时异常，以便全局异常处理器可以捕获
            throw new RuntimeException("Failed to deserialize object names JSON: " + e.getMessage());
        }
    }

    /**
     * 根据单个对象名构建 Image 对象。
     *
     * @param objectName     单个对象名
     * @param minioOperator  用于生成 URL 的 MinioOSSOperator 实例
     * @return 包含完整 URL 的 Image 对象
     */
    public static Image buildImage(String objectName, MinioOSSOperator minioOperator) {
        if (objectName == null || objectName.isEmpty()) {
            return null;
        }
        return new Image(
                objectName,
                minioOperator.toUrl(objectName),
                minioOperator.toThumbUrl(objectName)
        );
    }
}
