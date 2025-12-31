package hal.th50743.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import hal.th50743.mapper.ChatMapper;
import hal.th50743.mapper.ChatMemberMapper;
import hal.th50743.mapper.MessageMapper;
import hal.th50743.pojo.*;
import hal.th50743.service.ChatService;
import hal.th50743.utils.ImageUtils;
import hal.th50743.ws.WebSocketServer;
import io.minio.MinioOSSOperator;
import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 聊天服务实现类
 * <p>
 * 负责处理聊天室初始化、成员管理及详情获取逻辑。
 * 实现了 ChatService 接口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMapper chatMapper;
    private final ChatMemberMapper chatMemberMapper;
    private final MessageMapper messageMapper;
    private final MinioOSSOperator minioOSSOperator;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ============================================================
    // 1. 核心组装逻辑 (VO Assembly)
    // ============================================================

    /**
     * 核心复用方法：组装单个 ChatVO 的公共数据
     * <p>
     * 涵盖：头像 URL 转换、最后消息填充、未读数挂载、在线成员统计及脱敏。
     *
     * @param c           聊天室视图对象
     * @param onlineUsers 在线用户 Session 映射
     * @param lastMsg     最后一条消息
     * @param unreadCount 未读消息数
     * @param members     聊天室成员列表
     */
    private void assembleChatVO(ChatVO c,
                                Map<Integer, Session> onlineUsers,
                                MessageVO lastMsg,
                                Integer unreadCount,
                                List<ChatMember> members) {
        // A. 头像处理：将数据库存储的对象名转换为可访问的 MinIO URL
        if (c.getAvatarName() != null) {
            c.setAvatar(ImageUtils.buildImage(c.getAvatarName(), minioOSSOperator));
            c.setAvatarName(null); // 清理原始字段
        }

        // B. 最后消息处理：设置最后消息文本及活跃时间
        if (lastMsg != null) {
            // 构建图片列表 (即使 objectNames 为 null，工具类也会返回空列表，确保 images 字段不为 null)
            lastMsg.setImages(ImageUtils.buildImagesFromJson(lastMsg.getObjectNames(), objectMapper, minioOSSOperator));
            lastMsg.setObjectNames(null); // 清理原始 JSON 字符串

            c.setLastMessage(lastMsg);
            c.setLastActiveAt(lastMsg.getCreateTime());
        } else {
            c.setLastActiveAt(c.getCreateTime()); // 若无消息则以创建时间为准
        }

        // C. 未读数挂载
        c.setUnreadCount(unreadCount != null ? unreadCount : 0);

        // D. 成员处理与在线人数统计
        if (members != null) {
            // 通过比对全局 WebSocket 会话 Map 统计实时在线人数
            long onlineCount = members.stream()
                    .filter(m -> m.getUserId() != null && onlineUsers.containsKey(m.getUserId()))
                    .count();
            c.setOnLineMemberCount((int) onlineCount);
            members.forEach(m -> {
                // 1. 头像转换
                if (m.getAvatarObject() != null) {
                    m.setAvatar(ImageUtils.buildImage(m.getAvatarObject(), minioOSSOperator));
                    m.setAvatarObject(null); // 清理原始字段
                }
                // 2. 脱敏 (清理 userId)
                m.setUserId(null);
            });
            
            c.setChatMembers(members);
        }
    }

    // ============================================================
    // 2. 业务初始化逻辑 (App Initialization)
    // ============================================================

    /**
     * 获取用户的聊天列表及成员在线状态
     *
     * @param userId 用户ID
     * @return AppInitVO 包含聊天列表和用户状态列表
     */
    @Override
    public AppInitVO getChatVOListAndMemberStatusList(Integer userId) {
        // 获取用户加入的所有房间列表
        List<ChatVO> chatVOList = chatMapper.getChatVOListByUserId(userId);
        if (chatVOList == null || chatVOList.isEmpty()) return null;

        // 预拉取全局数据：在线用户快照、全量成员记录、未读数 Map、最后消息 Map
        Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();
        List<ChatMember> allMembers = chatMemberMapper.getChatMemberListByUserId(userId);

        // 按 ChatCode 对成员进行分组，优化 O(1) 查找性能
        Map<String, List<ChatMember>> chatMemberMap = allMembers.stream()
                .collect(Collectors.groupingBy(ChatMember::getChatCode));

        // 聚合未读消息数
        Map<String, Integer> unreadCountMap = messageMapper.getUnreadCountMapByUserId(userId).stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("chatCode"),
                        row -> ((Number) row.get("unreadCount")).intValue(),
                        (v1, v2) -> v1));

        // 聚合各房间最后一条消息
        Map<String, MessageVO> lastMessageMap = messageMapper.getLastMessageListByUserId(userId);

        // 处理全局唯一用户状态列表 (用于前端头像在线状态点展示)
        Map<String, UserStatus> uniqueUserStatusMap = new HashMap<>();
        for (ChatMember m : allMembers) {
            uniqueUserStatusMap.putIfAbsent(m.getUId(), new UserStatus(
                    m.getUId(), onlineUsers.containsKey(m.getUserId()), m.getLastSeenAt()));
        }

        // 遍历并调用组装逻辑
        for (ChatVO c : chatVOList) {
            assembleChatVO(c,
                    onlineUsers,
                    lastMessageMap.get(c.getChatCode()),
                    unreadCountMap.get(c.getChatCode()),
                    chatMemberMap.get(c.getChatCode()));
        }

        return new AppInitVO(chatVOList, new ArrayList<>(uniqueUserStatusMap.values()));
    }

    // ============================================================
    // 3. 房间详情与权限逻辑 (Chat Detail & Security)
    // ============================================================

    /**
     * 获取聊天室详情
     *
     * @param userId   用户ID
     * @param chatCode 聊天室代码
     * @return ChatVO 聊天室详情视图对象
     */
    @Override
    public ChatVO getChat(Integer userId, String chatCode) {
        // 1. 获取 ID 并执行权限校验
        Integer chatId = getChatId(userId, chatCode);

        // 2. 获取房间基础信息
        ChatVO chatVO = chatMapper.getChatVOByChatId(chatId);
        if (chatVO != null) {
            // 获取该房间所需的实时状态数据
            Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();
            List<ChatMember> members = chatMemberMapper.getChatMemberListByChatId(chatId);
            MessageVO lastMsg = messageMapper.getLastMessageByChatId(chatId);
            Integer unreadCount = messageMapper.getUnreadCountMapByUserIdAndChatId(userId, chatId);

            // 调用核心组装逻辑
            assembleChatVO(chatVO, onlineUsers, lastMsg, unreadCount, members);
        }
        return chatVO;
    }

    /**
     * 根据 ChatCode 获取 ChatId，并校验用户权限
     *
     * @param userId   用户ID
     * @param chatCode 聊天室代码
     * @return ChatId
     * @throws RuntimeException 如果聊天室不存在或用户无权访问
     */
    @Override
    public Integer getChatId(Integer userId, String chatCode) {
        // 查找 ChatID
        Integer chatId = chatMapper.getChatIdByChatCode(chatCode);
        if (chatId == null) {
            log.warn("[非法请求] 用户 {} 尝试访问不存在的 chatCode: {}", userId, chatCode);
            throw new RuntimeException("Invalid Request: Chat room not found");
        }

        // 校验成员关系 (防止越权访问)
        if (!chatMapper.isValidChatId(userId, chatId)) {
            log.warn("[权限拒绝] 用户 {} 尝试访问未加入的聊天室: {}", userId, chatCode);
            throw new RuntimeException("Permission denied: User is not a member of this chat room");
        }
        return chatId;
    }

    // ============================================================
    // 4. 其他逻辑
    // ============================================================

    /**
     * 获取用户的聊天成员ID列表
     *
     * @param userId 用户ID
     * @return 成员ID列表
     */
    @Override
    public List<Integer> getChatMembers(Integer userId) {
        return chatMemberMapper.getChatMembersById(userId);
    }

    /**
     * 加入聊天室
     *
     * @param joinChatReq 加入请求
     * @return Chat 聊天室对象
     */
    @Override
    public Chat join(JoinChatReq joinChatReq) {
        // TODO: 实现用户加入聊天室的业务逻辑
        return null;
    }
}
