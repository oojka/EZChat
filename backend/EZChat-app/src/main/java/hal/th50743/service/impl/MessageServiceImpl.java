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
 * 消息服务实现类 - 消息核心业务逻辑处理
 *
 * <h3>职责概述</h3>
 * <p>
 * 负责消息的发送、存储、查询和同步。作为实时通信的核心服务，
 * 与 WebSocket 服务和资产服务紧密协作。
 * </p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li><b>消息处理</b>：接收 WebSocket 消息，验证并存储</li>
 *     <li><b>消息存储</b>：生成 seqId，存储消息和附件关联</li>
 *     <li><b>消息查询</b>：基于 seqId 的游标分页查询</li>
 *     <li><b>消息同步</b>：拉取指定 seqId 之后的新消息</li>
 *     <li><b>图片上传</b>：消息图片上传（委托 AssetService）</li>
 * </ul>
 *
 * <h3>调用路径</h3>
 * <ul>
 *     <li>{@code WebSocketServer} → 本服务：实时消息处理</li>
 *     <li>{@code MessageController} → 本服务：消息查询和上传</li>
 * </ul>
 *
 * <h3>核心不变量</h3>
 * <ul>
 *     <li>seqId 在聊天室内单调递增且唯一</li>
 *     <li>消息类型：0=文本，1=图片，2=图文混合</li>
 *     <li>图片通过 assetIds（JSON 数组）关联 assets 表</li>
 * </ul>
 *
 * <h3>外部依赖</h3>
 * <ul>
 *     <li><b>ChatService</b>：验证聊天室成员资格</li>
 *     <li><b>AssetService</b>：图片上传和激活</li>
 *     <li><b>MessageAssembler</b>：填充消息附件信息</li>
 * </ul>
 *
 * @author 系统开发者
 * @since 1.0
 * @see WebSocketServer
 * @see MessageController
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
     * 处理 WebSocket 传入的新消息
     *
     * <p>验证用户是否为聊天室成员，存储消息并返回接收者列表。
     *
     * <h4>处理流程</h4>
     * <ol>
     *     <li>通过 chatCode 获取 chatId</li>
     *     <li>验证发送者是否为成员</li>
     *     <li>存储消息并获取 seqId</li>
     *     <li>返回所有成员 ID 用于广播</li>
     * </ol>
     *
     * @param userId     发送用户 ID
     * @param messageReq 消息请求对象（含 chatCode、text、images）
     * @return 处理结果，包含接收用户列表和 seqId；验证失败返回空列表
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
     * <p>生成 seqId，存储消息，并激活关联的图片文件。
     *
     * <h4>seqId 生成机制</h4>
     * <ol>
     *     <li>原子递增 chat_sequences 表的当前值</li>
     *     <li>获取递增后的值作为本消息的 seqId</li>
     * </ol>
     *
     * @param userId 发送用户 ID
     * @param chatId 聊天室内部 ID
     * @param text   文本内容（可为 null）
     * @param images 图片列表（可为 null）
     * @return 消息的 seqId
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
     * 根据聊天代码获取消息列表（游标分页）
     *
     * <p>进入聊天室时调用，返回指定 seqId 之前的消息，并更新用户的 lastSeenAt。
     *
     * @param userId      当前用户 ID
     * @param chatCode    聊天室代码
     * @param cursorSeqId 游标 seqId，获取此值之前的消息；null 表示获取最新消息
     * @return 消息列表 VO，包含消息列表和聊天室详情
     * @throws BusinessException CHAT_NOT_FOUND 或 NOT_A_MEMBER
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
     * 同步消息（拉取指定 seqId 之后的新消息）
     *
     * <p>用于客户端增量同步场景，获取 lastSeqId 之后的所有消息。
     *
     * @param userId    当前用户 ID
     * @param chatCode  聊天室代码
     * @param lastSeqId 上次同步的最后 seqId
     * @return lastSeqId 之后的消息列表（不含 lastSeqId）
     * @throws BusinessException CHAT_NOT_FOUND 或 NOT_A_MEMBER
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
     * 上传消息图片
     *
     * <p>上传后的图片初始状态为 PENDING，待消息发送成功后激活为 MESSAGE_IMG。
     *
     * @param file 图片文件
     * @return Image 对象，包含 assetName、url、thumbUrl、assetId
     */
    @Override
    public Image upload(MultipartFile file) {
        // 业务目的：消息图片上传统一交给 OSS 媒体服务处理（含图片规范化/缩略图/私有访问）
        return assetService.uploadMessageImage(file);
    }
}
