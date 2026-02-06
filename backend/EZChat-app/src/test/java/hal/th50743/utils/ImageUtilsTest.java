package hal.th50743.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import hal.th50743.pojo.Image;
import io.minio.MinioOSSOperator;
import io.minio.MinioOSSResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 图片工具类单元测试
 *
 * <p>覆盖以下场景:</p>
 * <ul>
 *     <li>扩展名推断 (guessExtension): 无扩展名、大写扩展名</li>
 *     <li>强制扩展名 (forceExtension): 空文件名处理</li>
 *     <li>图片规范化 (normalizeIfImage): 非图片绕过、GIF 绕过</li>
 *     <li>Image 对象构建 (buildImage, buildImagesFromJson)</li>
 * </ul>
 *
 * @see ImageUtils
 */
@ExtendWith(MockitoExtension.class)
class ImageUtilsTest {

    @Mock
    private MinioOSSOperator minioOSSOperator;

    /**
     * 扩展名推断 - 无扩展名返回空字符串，大写扩展名转小写
     */
    @Test
    void guessExtensionHandlesMissing() {
        assertEquals("", ImageUtils.guessExtension("file"));
        assertEquals(".png", ImageUtils.guessExtension("file.PNG"));
    }

    /**
     * 强制扩展名 - 空白文件名使用默认名称
     */
    @Test
    void forceExtensionHandlesBlankName() {
        assertEquals("file.jpg", ImageUtils.forceExtension("", ".jpg"));
        assertEquals("photo.png", ImageUtils.forceExtension("photo.png", ".png"));
    }

    /**
     * 规范化 - 非图片文件直接返回原始数据
     */
    @Test
    void normalizeIfImageBypassesNonImage() throws Exception {
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", bytes);

        ImageUtils.NormalizedFile normalized = ImageUtils.normalizeIfImage(file, 2048, 0.85);

        assertArrayEquals(bytes, normalized.bytes());
        assertEquals(".txt", normalized.extension());
        assertEquals("text/plain", normalized.contentType());
    }

    /**
     * 规范化 - GIF 文件跳过规范化保留动画
     */
    @Test
    void normalizeIfImageBypassesGif() throws Exception {
        byte[] bytes = "gif".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "anim.GIF", "image/gif", bytes);

        ImageUtils.NormalizedFile normalized = ImageUtils.normalizeIfImage(file, 2048, 0.85);

        assertArrayEquals(bytes, normalized.bytes());
        assertEquals(".gif", normalized.extension());
        assertEquals("image/gif", normalized.contentType());
    }

    /**
     * 构建 Image - null 输入返回 null
     */
    @Test
    void buildImageHandlesNull() {
        assertNull(ImageUtils.buildImage(null, minioOSSOperator));
    }

    /**
     * 构建 Image - 使用 MinIO 生成的 URL 和缩略图 URL
     */
    @Test
    void buildImageUsesMinioUrls() {
        when(minioOSSOperator.getImageUrls(eq("public/a.png"), anyInt(), eq(TimeUnit.MINUTES)))
                .thenReturn(new MinioOSSResult("public/a.png", "url", "thumb"));

        Image image = ImageUtils.buildImage("public/a.png", minioOSSOperator);

        assertEquals("public/a.png", image.getImageName());
        assertEquals("url", image.getImageUrl());
        assertEquals("thumb", image.getImageThumbUrl());
        assertNull(image.getAssetId());
    }

    /**
     * 从 JSON 构建 Image 列表 - 解析 JSON 数组并为每个元素构建 Image
     */
    @Test
    void buildImagesFromJsonParsesAndBuilds() {
        ObjectMapper objectMapper = new ObjectMapper();
        when(minioOSSOperator.getImageUrls(eq("public/a.png"), anyInt(), eq(TimeUnit.MINUTES)))
                .thenReturn(new MinioOSSResult("public/a.png", "url-a", "thumb-a"));
        when(minioOSSOperator.getImageUrls(eq("private/b.png"), anyInt(), eq(TimeUnit.MINUTES)))
                .thenReturn(new MinioOSSResult("private/b.png", "url-b", "thumb-b"));

        List<Image> images = ImageUtils.buildImagesFromJson("[\"public/a.png\",\"private/b.png\"]",
                objectMapper, minioOSSOperator);

        assertEquals(2, images.size());
        assertEquals("public/a.png", images.get(0).getImageName());
        assertEquals("url-a", images.get(0).getImageUrl());
        assertEquals("private/b.png", images.get(1).getImageName());
        assertEquals("url-b", images.get(1).getImageUrl());
    }

    /**
     * 从 JSON 构建 Image 列表 - 无效 JSON 抛出 RuntimeException
     */
    @Test
    void buildImagesFromJsonThrowsOnInvalidJson() {
        ObjectMapper objectMapper = new ObjectMapper();

        assertThrows(RuntimeException.class,
                () -> ImageUtils.buildImagesFromJson("not-json", objectMapper, minioOSSOperator));
    }
}
