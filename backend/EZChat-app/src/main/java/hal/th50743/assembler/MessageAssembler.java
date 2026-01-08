package hal.th50743.assembler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hal.th50743.pojo.Asset;
import hal.th50743.pojo.Image;
import hal.th50743.pojo.MessageVO;
import hal.th50743.service.AssetService;
import hal.th50743.utils.ImageUtils;
import io.minio.MinioOSSOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MessageAssembler
 * <p>
 * 负责 MessageVO 的组装和充血（如填充图片URL等）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageAssembler {

    private final AssetService assetService;
    private final MinioOSSOperator minioOSSOperator;
    private final ObjectMapper objectMapper = new ObjectMapper(); // Thread-safe

    /**
     * 批量填充消息列表中的附件/图片信息
     *
     * @param messageList 消息列表
     */
    public void fillMessageAssets(List<MessageVO> messageList) {
        if (messageList == null || messageList.isEmpty()) {
            return;
        }
        for (MessageVO m : messageList) {
            enrichMessage(m);
        }
    }

    /**
     * 填充单个消息的附件/图片信息
     *
     * @param m 消息对象
     */
    private void enrichMessage(MessageVO m) {
        String assetIdsJson = m.getAssetIds();
        if (assetIdsJson != null && !assetIdsJson.isEmpty()) {
            try {
                // 1. 反序列化为 assetId 列表
                List<Integer> assetIds = objectMapper.readValue(assetIdsJson, new TypeReference<List<Integer>>() {
                });

                // 2. 根据 assetId 列表查询 assets 表，构建 Image 对象列表
                List<Image> images = new ArrayList<>();
                for (Integer assetId : assetIds) {
                    Asset asset = assetService.findById(assetId);
                    if (asset != null) {
                        // 使用 ImageUtils.buildImage() 构建 Image 对象（包含 URL）
                        Image image = ImageUtils.buildImage(asset.getAssetName(), minioOSSOperator);
                        // 设置 assetId
                        if (image != null) {
                            image.setAssetId(assetId);
                            images.add(image);
                        }
                    } else {
                        log.warn("Asset not found by id: {}", assetId);
                    }
                }
                m.setImages(images);
            } catch (JsonProcessingException e) {
                log.error("反序列化图片对象ID列表失败: msgId={}, assetIds={}", m.getSeqId(), assetIdsJson, e);
                m.setImages(Collections.emptyList());
            }
            // 清理掉原始的JSON字符串，不需要返回给前端
            m.setAssetIds(null);
        }
    }
}
