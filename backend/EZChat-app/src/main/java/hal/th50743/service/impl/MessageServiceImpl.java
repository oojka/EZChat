package hal.th50743.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.mapper.ChatMapper;
import hal.th50743.mapper.ChatMemberMapper;
import hal.th50743.mapper.MessageMapper;
import hal.th50743.pojo.*;
import hal.th50743.service.ChatService;
import hal.th50743.service.AssetService;
import hal.th50743.service.MessageService;
import hal.th50743.utils.ImageUtils;
import io.minio.MinioOSSOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 消息服务实现类
 * <p>
 * 负责消息的发送、存储、查询及文件上传。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final ChatMapper chatMapper;
    private final ChatMemberMapper chatMemberMapper;
    private final MessageMapper messageMapper;
    private final MinioOSSOperator minioOSSOperator;
    private final AssetService assetService;

    // ObjectMapper是线程安全的，可以作为成员变量
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Lazy // 使用 @Lazy 解决可能的循环依赖，因为 ChatService 可能也依赖 MessageService
    private final ChatService chatService;

    /**
     * 处理WebSocket传入的新消息
     * 
     * @param userId     发送用户ID
     * @param messageReq 消息请求对象
     * @return 需要接收该消息的用户ID列表
     */
    @Override
    public List<Integer> handleWSMessage(Integer userId, MessageReq messageReq) {
        // 1. 通过聊天代码获取内部chatId
        Integer chatId = chatMapper.selectChatIdByChatCode(messageReq.getChatCode());
        if (chatId == null) {
            log.warn("handleWSMessage: 收到未知聊天室的消息, chatCode={}", messageReq.getChatCode());
            return Collections.emptyList(); // 如果聊天不存在，返回空列表
        }
        // 2. 验证用户是否是该聊天室成员，并获取所有成员列表用于消息推送
        List<Integer> senList = chatMemberMapper.selectChatMembersByUserIdAndChatId(userId, chatId);
        if (senList == null || senList.isEmpty()) {
            log.warn("handleWSMessage: 用户 {} 不是聊天室 {} 的成员，拒绝发送消息", userId, chatId);
            return Collections.emptyList();
        }

        // 3. 保存消息到数据库
        addMessage(userId, chatId, messageReq.getText(), messageReq.getImages());
        // 4. 返回需要接收消息的用户ID列表
        return senList;
    }

    /**
     * 添加消息到数据库
     * 
     * @param userId 发送用户ID
     * @param chatId 聊天ID
     * @param text   文本内容
     * @param images 从客户端传来的对象存储URL数组
     */
    @Override
    public void addMessage(Integer userId, Integer chatId, String text, List<Image> images) {
        log.info("add message, userId={}, chatId={}, text={}, images={}", userId, chatId, text, images);
        String assetIdsJson = null;
        // 如果有附件，则提取 objectId（直接使用 Image 对象的 objectId 字段）
        if (images != null && !images.isEmpty()) {
            List<Integer> objectIds = new ArrayList<>();
            for (Image image : images) {
                if (image.getAssetId() != null) {
                    objectIds.add(image.getAssetId());
                } else {
                    // 新工程中，上传接口必须返回 objectId，如果为 null 则记录错误
                    log.error("Image objectId is null, skipping image: {}", image.getImageName());
                }
            }
            if (!objectIds.isEmpty()) {
                try {
                    assetIdsJson = objectMapper.writeValueAsString(objectIds);
                } catch (JsonProcessingException e) {
                    log.error("序列化图片对象ID列表失败: ", e);
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                            "Failed to serialize image object IDs list: " + e.getMessage());
                }
            }
        }
        // Message type: 0=text, 1=image, 2=mixed
        boolean hasText = text != null && !text.isBlank();
        boolean hasImages = images != null && !images.isEmpty();
        int type = hasText && hasImages ? 2 : (hasImages ? 1 : 0);

        // 创建消息对象
        Message msg = new Message(
                null,
                userId,
                chatId,
                type,
                text,
                assetIdsJson, // 存储 assetId 列表的 JSON 字符串
                LocalDateTime.now(),
                LocalDateTime.now());
        // 将消息添加到数据库（useGeneratedKeys 会自动填充 msg.id）
        messageMapper.insertMessage(msg);

        // 消息保存成功后，批量激活关联的图片文件（status=1, category=MESSAGE_IMG, message_id=msg.id）
        if (images != null && !images.isEmpty()) {
            List<String> assetNames = new ArrayList<>();
            for (Image image : images) {
                assetNames.add(image.getImageName());
            }
            assetService.activateFilesBatch(assetNames, AssetCategory.MESSAGE_IMG, msg.getId());
            log.debug("Activated {} files for message: messageId={}", assetNames.size(), msg.getId());
        }
    }

    /**
     * 根据聊天代码获取未读消息列表
     * 
     * @param userId    当前用户ID
     * @param chatCode  聊天室的公开代码
     * @param timeStamp 可选的时间戳，用于获取该时间点之后的消息
     * @return 经过处理的消息视图对象列表
     */
    @Override
    public Map<String, Object> getMessagesByChatCode(Integer userId, String chatCode, String timeStamp) {
        // 1. 解析时间戳（如果存在）
        LocalDateTime createTime = null;
        if (timeStamp != null && !timeStamp.isEmpty()) {
            try {
                createTime = LocalDateTime.parse(timeStamp);
            } catch (Exception e) {
                log.warn("'非法的timeStamp'来自用户：{} 的非法的请求 chatCode:：{} 时间戳: {}", userId, chatCode, timeStamp);
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid request: Incorrect timestamp format");
            }
        }

        Integer chatId = chatService.getChatId(userId, chatCode);

        List<MessageVO> messageList = messageMapper.selectMessageListByChatIdAndTimeStamp(chatId, createTime);
        log.info("User:{} updat LastSeenAt:{}", userId, LocalDateTime.now());
        chatMemberMapper.updateLastSeenAt(userId, chatId, LocalDateTime.now());

        // 4. 对每条消息进行后处理，主要是处理附件URL
        for (MessageVO m : messageList) {
            String assetIdsJson = m.getAssetIds();
            if (assetIdsJson != null && !assetIdsJson.isEmpty()) {
                try {
                    // 1. 反序列化为 objectId 列表
                    List<Integer> assetIds = objectMapper.readValue(assetIdsJson, new TypeReference<List<Integer>>() {
                    });

                    // 2. 根据 objectId 列表查询 objects 表，构建 Image 对象列表
                    List<Image> images = new ArrayList<>();
                    for (Integer assetId : assetIds) {
                        Asset objectEntity = assetService.findById(assetId);
                        if (objectEntity != null) {
                            // 使用 ImageUtils.buildImage() 构建 Image 对象（包含 URL）
                            Image image = ImageUtils.buildImage(objectEntity.getAssetName(), minioOSSOperator);
                            // 设置 assetId（buildImage 返回的 Image 可能没有 assetId）
                            if (image != null) {
                                image.setAssetId(assetId);
                                images.add(image);
                            }
                        } else {
                            log.warn("Object not found by id: {}", assetId);
                        }
                    }
                    m.setImages(images);
                } catch (JsonProcessingException e) {
                    log.error("反序列化图片对象ID列表失败: {}", assetIdsJson, e);
                    // 不抛出异常，避免影响整条消息的显示
                    m.setImages(Collections.emptyList());
                }
                // 清理掉原始的JSON字符串，不需要返回给前端
                m.setAssetIds(null);
            }
        }

        // 调用 ChatService 获取完整的 ChatVO (包含头像转换、成员列表等)
        ChatVO chatVO = chatService.getChat(userId, chatCode);

        Map<String, Object> result = new HashMap<>();
        result.put("messageList", messageList);
        result.put("chatRoom", chatVO);
        return result;
    }

    /**
     * 消息的上传文件
     * 存在private路径中
     * 
     * @param file 文件对象
     * @return Image 图片对象
     */
    @Override
    public Image upload(MultipartFile file) {
        // 业务目的：消息图片上传统一交给 OSS 媒体服务处理（含图片规范化/缩略图/私有访问）
        return assetService.uploadMessageImage(file);
    }
}
