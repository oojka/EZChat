package hal.th50743.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.mapper.ChatMapper;
import hal.th50743.mapper.ChatMemberMapper;
import hal.th50743.mapper.MessageMapper;
import hal.th50743.pojo.*;
import hal.th50743.service.ChatService;
import hal.th50743.service.MessageService;
import hal.th50743.service.OssMediaService;
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
    private final OssMediaService ossMediaService;

    // ObjectMapper是线程安全的，可以作为成员变量
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Lazy // 使用 @Lazy 解决可能的循环依赖，因为 ChatService 可能也依赖 MessageService
    private final ChatService chatService;


    /**
     * 处理WebSocket传入的新消息
     * @param userId 发送用户ID
     * @param messageReq 消息请求对象
     * @return 需要接收该消息的用户ID列表
     */
    @Override
    public List<Integer> handleWSMessage(Integer userId, MessageReq messageReq) {
        // 1. 通过聊天代码获取内部chatId
        Integer chatId = chatMapper.getChatIdByChatCode(messageReq.getChatCode());
        if (chatId == null) {
            log.warn("handleWSMessage: 收到未知聊天室的消息, chatCode={}", messageReq.getChatCode());
            return Collections.emptyList(); // 如果聊天不存在，返回空列表
        }
        // 2. 验证用户是否是该聊天室成员，并获取所有成员列表用于消息推送
        List<Integer> senList = chatMemberMapper.getChatMembersByUserIdAndChatId(userId, chatId);
        if (senList == null || senList.isEmpty()) {
            log.warn("handleWSMessage: 用户 {} 不是聊天室 {} 的成员，拒绝发送消息", userId, chatId);
            return Collections.emptyList();
        }

        // 3. 保存消息到数据库
        saveMessage(userId, chatId, messageReq.getText(), messageReq.getImages());
        // 4. 返回需要接收消息的用户ID列表
        return senList;
    }

    /**
     * 保存消息到数据库
     * @param userId 发送用户ID
     * @param chatId 聊天ID
     * @param text 文本内容
     * @param images 从客户端传来的对象存储URL数组
     */
    @Override
    public void saveMessage(Integer userId, Integer chatId, String text, List<Image> images) {
        log.info("save message, userId={}, chatId={}, text={}, images={}", userId, chatId, text, images);
        String objectNamesJson = null;
        // 如果有附件URL，则转换为内部存储的对象名
        if (images != null && !images.isEmpty()) {
            List<String> objectNames = new ArrayList<>();
            for (Image image : images) {
                objectNames.add(minioOSSOperator.toObjectName(image.getObjectUrl()));
            }
            try {
                objectNamesJson = objectMapper.writeValueAsString(objectNames);
            } catch (JsonProcessingException e) {
                log.error("序列化对象名称列表失败: ", e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Failed to serialize object names list: " + e.getMessage());
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
                objectNamesJson, // 存储JSON字符串
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        // 将消息添加到数据库
        messageMapper.addMessage(msg);
    }

    /**
     * 根据聊天代码获取未读消息列表
     * @param userId 当前用户ID
     * @param chatCode 聊天室的公开代码
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
                log.warn("'非法的timeStamp'来自用户：{} 的非法的请求 chatCode:：{} 时间戳: {}",userId, chatCode, timeStamp);
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid request: Incorrect timestamp format");
            }
        }

        Integer chatId = chatService.getChatId(userId, chatCode);


        List<MessageVO> messageList = messageMapper.getMessageListByChatIdAndTimeStamp(chatId, createTime);
        log.info("User:{} updat LastSeenAt:{}",userId, LocalDateTime.now());
        chatMemberMapper.updateLastSeenAt(userId, chatId, LocalDateTime.now());
        
        // 4. 对每条消息进行后处理，主要是处理附件URL
        for (MessageVO m : messageList) {
            String objectNamesJson = m.getObjectNames();
            if (objectNamesJson != null && !objectNamesJson.isEmpty()) {
                // 使用 ImageUtils 工具类处理图片列表
                List<Image> images = ImageUtils.buildImagesFromJson(objectNamesJson, objectMapper, minioOSSOperator);
                m.setImages(images);
                // 清理掉原始的JSON字符串，不需要返回给前端
                m.setObjectNames(null);
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
     * @param file 文件对象
     * @return Image 图片对象
     */
    @Override
    public Image upload(MultipartFile file) {
        // 业务目的：消息图片上传统一交给 OSS 媒体服务处理（含图片规范化/缩略图/私有访问）
        return ossMediaService.uploadMessageImage(file);
    }
}
