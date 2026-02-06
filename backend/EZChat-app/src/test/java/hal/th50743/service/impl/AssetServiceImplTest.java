package hal.th50743.service.impl;

import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.mapper.AssetMapper;
import hal.th50743.pojo.Asset;
import hal.th50743.pojo.AssetCategory;
import hal.th50743.pojo.Image;
import hal.th50743.service.AssetService;
import hal.th50743.utils.ObjectHashUtils;
import io.minio.MinioOSSOperator;
import io.minio.MinioOSSResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资产服务单元测试
 *
 * <p>覆盖以下场景:</p>
 * <ul>
 *     <li>文件上传 (uploadFile): 空文件校验、哈希去重、新文件持久化</li>
 *     <li>图片URL获取 (getImageUrl): 参数校验、MinIO URL 生成</li>
 *     <li>资产存在性检查 (checkAssetExists): 原始哈希优先、规范化哈希回退</li>
 *     <li>文件激活与GC (activateFile, findPendingFilesForGC): 状态更新与垃圾回收</li>
 * </ul>
 *
 * @see AssetServiceImpl
 */
@ExtendWith(MockitoExtension.class)
class AssetServiceImplTest {

    @Mock
    private MinioOSSOperator minioOSSOperator;

    @Mock
    private AssetMapper assetMapper;

    private AssetServiceImpl assetService;

    @BeforeEach
    void setUp() {
        assetService = new AssetServiceImpl(minioOSSOperator, assetMapper);
    }

    // ================================
    // 文件上传测试 (uploadFile)
    // ================================

    /**
     * 上传空文件时抛出 FILE_EMPTY 异常
     */
    @Test
    void uploadFileEmptyThrows() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.gif", "image/gif", new byte[0]);

        BusinessException ex = assertThrows(BusinessException.class, () -> assetService.uploadFile(file, true));

        assertEquals(ErrorCode.FILE_EMPTY.getCode(), ex.getCode());
    }

    /**
     * 上传已存在资产时复用 - 跳过 MinIO 上传和数据库插入
     */
    @Test
    void uploadFileReuseExistingAsset() {
        byte[] bytes = "gif-data".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "pic.gif", "image/gif", bytes);
        String hash = ObjectHashUtils.calculateSHA256(bytes);

        Asset existing = new Asset();
        existing.setId(9);
        existing.setAssetName("public/images/exist.gif");
        when(assetMapper.selectByNormalizedHashAndActive(hash)).thenReturn(existing);
        when(minioOSSOperator.getImageUrls(eq("public/images/exist.gif"), anyInt(), eq(TimeUnit.MINUTES)))
                .thenReturn(new MinioOSSResult("public/images/exist.gif", "url", "thumb"));

        AssetService.StoredObject result = assetService.uploadFile(file, true);

        assertEquals("public/images/exist.gif", result.objectName());
        assertEquals("url", result.url());
        assertEquals("thumb", result.thumbUrl());
        assertEquals("image/gif", result.contentType());
        verify(minioOSSOperator, never()).upload(any(), any(), any(), any(), anyInt(), anyInt());
        verify(assetMapper, never()).insertAsset(any(Asset.class));
    }

    /**
     * 上传新资产时持久化记录 - 上传到 MinIO 并插入数据库
     */
    @Test
    void uploadFileNewAssetPersistsRecord() {
        byte[] bytes = "gif-data".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "pic.gif", "image/gif", bytes);
        when(assetMapper.selectByNormalizedHashAndActive(any())).thenReturn(null);
        when(minioOSSOperator.upload(any(), eq("pic.gif"), eq("image/gif"), eq(false), anyInt(), anyInt()))
                .thenReturn(new MinioOSSResult("private/path/pic.gif", "url", "thumb"));
        when(assetMapper.insertAsset(any(Asset.class))).thenReturn(1);

        AssetService.StoredObject result = assetService.uploadFile(file, false);

        assertEquals("private/path/pic.gif", result.objectName());
        assertEquals("url", result.url());
        assertEquals("thumb", result.thumbUrl());
        assertEquals("image/gif", result.contentType());

        ArgumentCaptor<Asset> assetCaptor = ArgumentCaptor.forClass(Asset.class);
        verify(assetMapper).insertAsset(assetCaptor.capture());
        assertEquals("private/path/pic.gif", assetCaptor.getValue().getAssetName());
        assertEquals(AssetCategory.GENERAL.getValue(), assetCaptor.getValue().getCategory());
        assertEquals(Integer.valueOf(0), assetCaptor.getValue().getStatus());
    }

    // ================================
    // 图片URL获取测试 (getImageUrl)
    // ================================

    /**
     * 获取图片URL - 校验文件名格式 (空白和无效前缀)
     */
    @Test
    void getImageUrlValidatesName() {
        BusinessException blank = assertThrows(BusinessException.class, () -> assetService.getImageUrl(" "));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), blank.getCode());

        BusinessException badPrefix = assertThrows(BusinessException.class,
                () -> assetService.getImageUrl("images/a.png"));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), badPrefix.getCode());
    }

    /**
     * 获取图片URL - 返回 MinIO 生成的预签名URL
     */
    @Test
    void getImageUrlReturnsMinioUrl() {
        when(minioOSSOperator.getImageUrls(eq("public/images/a.png"), anyInt(), eq(TimeUnit.MINUTES)))
                .thenReturn(new MinioOSSResult("public/images/a.png", "url", "thumb"));

        String url = assetService.getImageUrl("public/images/a.png");

        assertEquals("url", url);
    }

    /**
     * 获取图片URL并附带 assetId - 返回完整 Image 对象
     */
    @Test
    void getImageUrlWithAssetIdReturnsImage() {
        Asset asset = new Asset();
        asset.setId(5);
        asset.setAssetName("public/images/a.png");
        when(assetMapper.selectByAssetName("public/images/a.png")).thenReturn(asset);
        when(minioOSSOperator.getImageUrls(eq("public/images/a.png"), anyInt(), eq(TimeUnit.MINUTES)))
                .thenReturn(new MinioOSSResult("public/images/a.png", "url", "thumb"));

        Image image = assetService.getImageUrlWithAssetId("public/images/a.png");

        assertEquals("public/images/a.png", image.getImageName());
        assertEquals("url", image.getImageUrl());
        assertEquals("thumb", image.getImageThumbUrl());
        assertEquals(Integer.valueOf(5), image.getAssetId());
    }

    // ================================
    // 资产存在性检查测试 (checkAssetExists)
    // ================================

    /**
     * 检查资产存在 - 优先使用原始哈希查询
     */
    @Test
    void checkAssetExistsUsesRawHashFirst() {
        Asset asset = new Asset();
        asset.setId(7);
        asset.setAssetName("public/images/a.png");
        when(assetMapper.selectByRawHashAndActive("raw-hash")).thenReturn(asset);
        when(minioOSSOperator.getImageUrls(eq("public/images/a.png"), anyInt(), eq(TimeUnit.MINUTES)))
                .thenReturn(new MinioOSSResult("public/images/a.png", "url", "thumb"));

        Image image = assetService.checkAssetExists("raw-hash");

        assertEquals("public/images/a.png", image.getImageName());
        assertEquals(Integer.valueOf(7), image.getAssetId());
    }

    /**
     * 检查资产存在 - 原始哈希未匹配时回退到规范化哈希
     */
    @Test
    void checkAssetExistsUsesNormalizedHashFallback() {
        Asset asset = new Asset();
        asset.setId(8);
        asset.setAssetName("public/images/b.png");
        when(assetMapper.selectByRawHashAndActive("raw-hash")).thenReturn(null);
        when(assetMapper.selectByNormalizedHashAndActive("raw-hash")).thenReturn(asset);
        when(minioOSSOperator.getImageUrls(eq("public/images/b.png"), anyInt(), eq(TimeUnit.MINUTES)))
                .thenReturn(new MinioOSSResult("public/images/b.png", "url", "thumb"));

        Image image = assetService.checkAssetExists("raw-hash");

        assertEquals("public/images/b.png", image.getImageName());
        assertEquals(Integer.valueOf(8), image.getAssetId());
    }

    // ================================
    // 文件保存与激活测试
    // ================================

    /**
     * 保存文件 - 创建待激活状态的资产记录
     */
    @Test
    void saveFilePersistsPendingRecord() {
        when(assetMapper.insertAsset(any(Asset.class))).thenReturn(1);

        Asset asset = assetService.saveFile(
                "public/images/a.png",
                "a.png",
                "image/png",
                12L,
                AssetCategory.USER_AVATAR,
                "raw",
                "normalized"
        );

        assertEquals("public/images/a.png", asset.getAssetName());
        assertEquals(AssetCategory.USER_AVATAR.getValue(), asset.getCategory());
        assertEquals(Integer.valueOf(0), asset.getStatus());
        verify(assetMapper).insertAsset(any(Asset.class));
    }

    /**
     * 批量激活文件 - 空列表时不执行任何操作
     */
    @Test
    void activateFilesBatchNoopOnEmpty() {
        assetService.activateFilesBatch(null, AssetCategory.MESSAGE_IMG, 1);
        assetService.activateFilesBatch(java.util.List.of(), AssetCategory.MESSAGE_IMG, 1);

        verify(assetMapper, never()).updateStatusBatch(any(), any(), any(), any());
    }

    /**
     * 激活单个文件 - 更新状态和分类
     */
    @Test
    void activateFileUpdatesMapper() {
        when(assetMapper.updateStatusAndCategory("public/images/a.png", 1, AssetCategory.CHAT_COVER.getValue()))
                .thenReturn(1);

        assetService.activateFile("public/images/a.png", AssetCategory.CHAT_COVER);

        verify(assetMapper).updateStatusAndCategory("public/images/a.png", 1, AssetCategory.CHAT_COVER.getValue());
    }

    // ================================
    // GC 与查询测试
    // ================================

    /**
     * 查找待GC文件 - 委托给 Mapper 执行
     */
    @Test
    void findPendingFilesForGCUsesMapper() {
        assetService.findPendingFilesForGC(24, 100, 0);

        verify(assetMapper).selectPendingFilesBefore(any(), eq(100), eq(0));
    }

    /**
     * 按ID和名称查询 - 委托给 Mapper 执行
     */
    @Test
    void findByIdAndNameDelegatesToMapper() {
        assetService.findById(1);
        assetService.findByAssetName("public/images/a.png");

        verify(assetMapper).selectById(1);
        verify(assetMapper).selectByAssetName("public/images/a.png");
    }
}
