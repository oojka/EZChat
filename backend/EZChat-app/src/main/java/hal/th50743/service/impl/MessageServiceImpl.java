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
import hal.th50743.service.AssetService;
import hal.th50743.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final AssetService assetService;
    private final hal.th50743.assembler.MessageAssembler messageAssembler;

    // ObjectMapper是线程安全的，可以作为成员变量
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Lazy // 使用 @Lazy 解决可能的循环依赖，因为 ChatService 可能也依赖 MessageService
    private final ChatService chatService;

    /**
     * 处理WebSocket传入的新消息
     * 
     * @param userId     发送用户ID
     * @param messageReq 消息请求对象
     * @return 处理结果（包含接收用户列表和 seqId）
     */
    @Override
    public WSMessageResult handleWSMessage(Integer userId, MessageReq messageReq) {
        // 1. 通过聊天代码获取内部chatId
        Integer chatId = chatMapper.selectChatIdByChatCode(messageReq.getChatCode());
        if (chatId == null) {
            log.warn("handleWSMessage: Received message for unknown chat room, chatCode={}", messageReq.getChatCode());
            return new WSMessageResult(Collections.emptyList(), null); // 如果聊天不存在，返回空列表
        }
        // 2. 验证用户是否是该聊天室成员，并获取所有成员列表用于消息推送
        List<Integer> senList = chatMemberMapper.selectChatMembersByUserIdAndChatId(userId, chatId);
        if (senList == null || senList.isEmpty()) {
            log.warn("handleWSMessage: User {} is not a member of chat room {}, refusing to send message", userId,
                    chatId);
            return new WSMessageResult(Collections.emptyList(), null);
        }

        // 3. 保存消息到数据库，并获取 seqId
        Long seqId = addMessage(userId, chatId, messageReq.getText(), messageReq.getImages());
        // 4. 返回处理结果
        return new WSMessageResult(senList, seqId);
    }

    /**
     * 添加消息到数据库
     * 
     * @param userId 发送用户ID
     * @param chatId 聊天ID
     * @param text   文本内容
     * @param images 从客户端传来的对象存储URL数组
     * @return 消息的 Sequence ID
     */
    @Override
    @Transactional
    public Long addMessage(Integer userId, Integer chatId, String text, List<Image> images) {
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
                    log.error("Failed to serialize image object ID list: ", e);
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                            "Failed to serialize image object IDs list: " + e.getMessage());
                }
            }
        }
        // Message type: 0=text, 1=image, 2=mixed
        boolean hasText = text != null && !text.isBlank();
        boolean hasImages = images != null && !images.isEmpty();
        int type = hasText && hasImages ? 2 : (hasImages ? 1 : 0);

        // 1. 更新当前群的消息序列号 (Atomic increment)
        messageMapper.updateChatSequence(chatId);

        // 2. 获取更新后的序列号
        Long currentSeq = messageMapper.selectCurrentSequence(chatId);

        // 创建消息对象
        Message msg = new Message(
                null,
                userId,
                chatId,
                currentSeq, // 设置 seqId
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

        return currentSeq;
    }

    /**
     * 根据聊天代码获取未读消息列表
     * 
     * @param userId   当前用户ID
     * @param chatCode 聊天室的公开代码
     * @return 经过处理的消息视图对象列表
     */
    @Override
    public MessageListVO getMessagesByChatCode(Integer userId, String chatCode, Long cursorSeqId) {
        Integer chatId = chatService.getChatId(userId, chatCode);

        // 使用 cursorSeqId 进行分页查询
        List<MessageVO> messageList = messageMapper.selectMessageListByChatIdAndCursor(chatId, cursorSeqId);
        // log.info("User:{} updat LastSeenAt:{}", userId, LocalDateTime.now());
        chatMemberMapper.updateLastSeenAt(userId, chatId, LocalDateTime.now());

        // log.info("User:{} updat LastSeenAt:{}", userId, LocalDateTime.now());
        chatMemberMapper.updateLastSeenAt(userId, chatId, LocalDateTime.now());

        // 4. 使用 Assembler 填充附件信息
        messageAssembler.fillMessageAssets(messageList);

        // 调用 ChatService 获取完整的 ChatVO (包含头像转换、成员列表等)
        ChatVO chatVO = chatService.getChat(userId, chatCode);

        return new MessageListVO(messageList, chatVO);
    }

    /**
     * 同步消息（拉取指定序列号之后的消息）
     *
     * @param userId    当前用户ID
     * @param chatCode  聊天室代码
     * @param lastSeqId 上次同步的最后序列号
     * @return 消息列表
     */
    @Override
    public List<MessageVO> syncMessages(Integer userId, String chatCode, Long lastSeqId) {
        // 1. 获取 ChatId
        Integer chatId = chatService.getChatId(userId, chatCode);

        // 2. 查询消息
        List<MessageVO> messageList = messageMapper.selectMessagesAfterSeqId(chatId, lastSeqId);

        // 3. 填充附件信息
        messageAssembler.fillMessageAssets(messageList);

        return messageList;
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
