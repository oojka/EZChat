package hal.th50743.assembler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hal.th50743.pojo.*;
import hal.th50743.service.AssetService;
import hal.th50743.utils.ImageUtils;
import io.minio.MinioOSSOperator;
import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 聊天室视图对象组装器
 * <p>
 * 负责将 ChatVO 视图对象进行组装，包括头像 URL 构建、最后消息处理等。
 * 成员相关的组装逻辑已抽取到 {@link ChatMemberAssembler}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAssembler {

    private final MinioOSSOperator minioOSSOperator;
    private final AssetService assetService;
    private final ObjectMapper objectMapper;
    private final ChatMemberAssembler chatMemberAssembler;

    /**
     * 组装完整的 ChatVO（包含成员列表）
     *
     * @param chatVO      聊天室视图对象
     * @param onlineUsers 在线用户 Map
     * @param lastMsg     最后一条消息
     * @param unreadCount 未读消息数
     * @param members     成员列表
     */
    public void assemble(ChatVO chatVO,
            Map<Integer, Session> onlineUsers,
            MessageVO lastMsg,
            Integer unreadCount,
            List<ChatMember> members) {
        // A. 头像处理
        processChatAvatar(chatVO);

        // B. 最后消息处理
        processLastMessage(chatVO, lastMsg);

        // C. 未读数挂载
        chatVO.setUnreadCount(unreadCount != null ? unreadCount : 0);

        // D. 成员处理与在线人数统计
        if (members != null) {
            List<ChatMemberVO> memberVOList = new ArrayList<>();
            int onlineCount = 0;

            for (ChatMember m : members) {
                boolean isOnline = chatMemberAssembler.isUserOnline(m.getUserId(), onlineUsers);
                if (isOnline) {
                    onlineCount++;
                }
                memberVOList.add(chatMemberAssembler.toChatMemberVO(m, isOnline));
            }

            chatVO.setOnLineMemberCount(onlineCount);
            chatVO.setChatMembers(memberVOList);
        }
    }

    /**
     * 组装精简版 ChatVO（不含成员列表，用于列表场景）
     *
     * @param chatVO      聊天室视图对象
     * @param lastMsg     最后一条消息
     * @param unreadCount 未读消息数
     * @param onlineCount 在线人数（预先计算）
     */
    public void assembleLite(ChatVO chatVO,
            MessageVO lastMsg,
            Integer unreadCount,
            Integer onlineCount) {
        processChatAvatar(chatVO);
        processLastMessage(chatVO, lastMsg);
        chatVO.setUnreadCount(unreadCount != null ? unreadCount : 0);
        chatVO.setOnLineMemberCount(onlineCount != null ? onlineCount : 0);
        chatVO.setChatMembers(null);
    }

    /**
     * 清理公开返回的 ChatVO 敏感数据
     * <p>
     * 用于 validateChatJoin 等接口，确保不泄露 ownerUid, joinEnabled 等信息
     *
     * @param chatVO 聊天室视图对象
     */
    public void sanitizeForPublic(ChatVO chatVO) {
        if (chatVO == null) {
            return;
        }
        chatVO.setOwnerUid(null);
        chatVO.setJoinEnabled(null);
        chatVO.setPasswordEnabled(null);
        chatVO.setLastActiveAt(null);
        chatVO.setCreateTime(null);
        chatVO.setUpdateTime(null);
        chatVO.setUnreadCount(null);
        chatVO.setLastMessage(null);
        chatVO.setOnLineMemberCount(null);
        chatVO.setChatMembers(null);
    }

    /**
     * 处理聊天室头像
     */
    private void processChatAvatar(ChatVO chatVO) {
        if (chatVO.getAvatarAssetName() != null) {
            chatVO.setAvatar(ImageUtils.buildImage(chatVO.getAvatarAssetName(), minioOSSOperator));
            chatVO.setAvatarAssetName(null); // 为了安全或减少传输，清除原始 ObjectName
        }
    }

    /**
     * 处理最后消息（包含图片解析）
     */
    private void processLastMessage(ChatVO chatVO, MessageVO lastMsg) {
        if (lastMsg != null) {
            String objectIdsJson = lastMsg.getAssetIds();
            if (objectIdsJson != null && !objectIdsJson.isEmpty()) {
                try {
                    List<Integer> objectIds = objectMapper.readValue(objectIdsJson, new TypeReference<List<Integer>>() {
                    });
                    List<Image> images = new ArrayList<>();
                    for (Integer objectId : objectIds) {
                        Asset objectEntity = assetService.findById(objectId);
                        if (objectEntity != null) {
                            Image image = ImageUtils.buildImage(objectEntity.getAssetName(), minioOSSOperator);
                            if (image != null) {
                                image.setAssetId(objectId);
                                images.add(image);
                            }
                        }
                    }
                    lastMsg.setImages(images);
                } catch (JsonProcessingException e) {
                    log.error("反序列化最后消息的图片对象ID列表失败: {}", objectIdsJson, e);
                    lastMsg.setImages(Collections.emptyList());
                }
            }
            lastMsg.setAssetIds(null);
            chatVO.setLastMessage(lastMsg);
            chatVO.setLastActiveAt(lastMsg.getCreateTime());
        } else {
            chatVO.setLastActiveAt(chatVO.getCreateTime());
        }
    }
}
