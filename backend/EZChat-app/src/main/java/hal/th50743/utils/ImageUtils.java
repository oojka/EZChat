package hal.th50743.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hal.th50743.pojo.Image;
import io.minio.MinioOSSOperator;
import io.minio.MinioOSSResult;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
     * 默认的图片预签名有效期（分钟）
     * <p>
     * 业务原因：消息列表/头像等场景会频繁请求图片，过短会导致大量刷新签名；过长会增加泄露风险。
     */
    private static final int DEFAULT_IMAGE_URL_EXPIRY_MINUTES = 30;

    /**
     * 规范化后的图片扩展名
     */
    private static final String NORMALIZED_IMAGE_EXTENSION = ".jpg";

    /**
     * 规范化后的图片 Content-Type
     */
    private static final String NORMALIZED_IMAGE_CONTENT_TYPE = "image/jpeg";

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
        // 统一走 getImageUrls：能自动区分 public/private，且在缩略图不存在时会回退到原图，避免前端必 404
        MinioOSSResult result = minioOperator.getImageUrls(objectName, DEFAULT_IMAGE_URL_EXPIRY_MINUTES, TimeUnit.MINUTES);
        return new Image(result.getObjectName(), result.getUrl(), result.getThumbUrl());
    }

    /**
     * 图片规范化（仅对图片执行；非图片原样返回）
     * <p>
     * 业务目的：
     * - 自动旋转（EXIF Orientation），避免手机拍照方向错误
     * - 去除元数据/EXIF（隐私保护），通过重编码输出 JPEG
     * - 统一输出 JPEG，降低前端/对象存储兼容复杂度
     * - 限制最大边，避免超大图带来的带宽与存储压力
     *
     * 重要说明：
     * - GIF 动图转 JPEG 会丢失动图，这里保守处理为“原样返回”
     * - 若解析失败（ImageIO 读不到），也会回退原样返回（不阻断上传）
     *
     * @param file         上传文件
     * @param maxDimension 最大边限制（例如 2048）
     * @param quality      JPEG 质量（0~1）
     * @return 规范化结果（bytes/extension/contentType）
     * @throws IOException 读取文件 bytes 可能抛出
     */
    public static NormalizedFile normalizeIfImage(MultipartFile file, int maxDimension, double quality) throws IOException {
        if (file == null || file.isEmpty()) {
            return new NormalizedFile(new byte[0], "", "");
        }

        String contentType = file.getContentType();
        String lowerContentType = contentType == null ? "" : contentType.toLowerCase();
        boolean isImage = lowerContentType.startsWith("image/");

        // GIF 动图：不做转换，避免丢失动效
        if ("image/gif".equals(lowerContentType)) {
            byte[] bytes = file.getBytes();
            return new NormalizedFile(bytes, guessExtension(file.getOriginalFilename()), contentType);
        }

        if (!isImage) {
            byte[] bytes = file.getBytes();
            return new NormalizedFile(bytes, guessExtension(file.getOriginalFilename()), contentType);
        }

        byte[] inputBytes = file.getBytes();

        // 1) 尝试解析图片（无法解析则回退原样上传）
        BufferedImage image;
        try (ByteArrayInputStream in = new ByteArrayInputStream(inputBytes)) {
            image = ImageIO.read(in);
        }
        if (image == null) {
            return new NormalizedFile(inputBytes, guessExtension(file.getOriginalFilename()), contentType);
        }

        // 2) 使用 Thumbnailator 重编码输出 JPEG：达到“旋转修正 + 去元数据 + 统一格式”
        try (ByteArrayInputStream input = new ByteArrayInputStream(inputBytes);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            var builder = Thumbnails.of(input)
                    // 关键：根据 EXIF Orientation 自动旋转
                    .useExifOrientation(true)
                    // 强制输出 JPEG
                    .outputFormat("jpg")
                    // 质量压缩（在体积与观感之间取平衡）
                    .outputQuality(quality);

            // 重要约束（业务要求）：不降低分辨率（不做任何 resize/downscale）
            // 这里固定 scale(1.0)，仅做重编码（JPEG）、EXIF 方向修正与去元数据。
            // 说明：maxDimension 参数保留用于兼容旧调用方（未来如需限制大图可再引入开关）。
            builder.scale(1.0);

            builder.toOutputStream(output);
            byte[] outBytes = output.toByteArray();
            return new NormalizedFile(outBytes, NORMALIZED_IMAGE_EXTENSION, NORMALIZED_IMAGE_CONTENT_TYPE);
        } catch (Exception e) {
            // 任何处理失败都不应该阻断上传：回退原图（但记录日志方便定位）
            log.warn("图片规范化失败，回退原文件。name={}", file.getOriginalFilename(), e);
            return new NormalizedFile(inputBytes, guessExtension(file.getOriginalFilename()), contentType);
        }
    }

    /**
     * 强制替换文件扩展名
     * <p>
     * 业务目的：当图片被规范化为 JPEG 时，确保对象名后缀与实际内容一致（避免按后缀判断类型时出现偏差）。
     *
     * @param fileName   原始文件名
     * @param extension  目标扩展名（例如 ".jpg"）
     * @return 替换后文件名
     */
    public static String forceExtension(String fileName, String extension) {
        if (fileName == null || fileName.isBlank()) return "file" + (extension == null ? "" : extension);
        if (extension == null || extension.isBlank()) return fileName;
        int dot = fileName.lastIndexOf('.');
        String base = dot == -1 ? fileName : fileName.substring(0, dot);
        return base + extension;
    }

    /**
     * 从文件名推断扩展名
     *
     * @param filename 文件名
     * @return 扩展名（包含 "."），无法推断则返回空串
     */
    public static String guessExtension(String filename) {
        if (filename == null || filename.isBlank()) return "";
        int dot = filename.lastIndexOf('.');
        if (dot == -1 || dot == filename.length() - 1) return "";
        return filename.substring(dot).toLowerCase();
    }

    /**
     * 规范化结果 DTO
     *
     * @param bytes       输出字节数组
     * @param extension   输出扩展名（图片规范化后通常为 ".jpg"）
     * @param contentType 输出 Content-Type（图片规范化后通常为 "image/jpeg"）
     */
    public record NormalizedFile(byte[] bytes, String extension, String contentType) {
    }
}
