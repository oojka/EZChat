package hal.th50743.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.mapper.ChatInviteMapper;
import hal.th50743.mapper.ChatMapper;
import hal.th50743.mapper.ChatMemberMapper;
import hal.th50743.mapper.MessageMapper;
import hal.th50743.pojo.*;
import hal.th50743.pojo.FileEntity;
import hal.th50743.service.ChatService;
import hal.th50743.service.FileService;
import hal.th50743.service.UserService;
import hal.th50743.utils.*;
import hal.th50743.ws.WebSocketServer;
import io.minio.MinioOSSOperator;
import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final ChatInviteMapper chatInviteMapper;
    private final MessageMapper messageMapper;
    private final MinioOSSOperator minioOSSOperator;
    private final UserService userService;
    private final FileService fileService;
    private final JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ============================================================
    // 1. 核心组装逻辑 (VO Assembly)
    // ============================================================

    /**
     * 核心复用方法：组装单个 ChatVO 的公共数据
     */
    private void chatVOBuilder(ChatVO c,
                               Map<Integer, Session> onlineUsers,
                               MessageVO lastMsg,
                               Integer unreadCount,
                               List<ChatMember> members) {
        // A. 头像处理
        if (c.getAvatarObjectName() != null) {
            c.setAvatar(ImageUtils.buildImage(c.getAvatarObjectName(), minioOSSOperator));
            c.setAvatarObjectName(null);
        }

        // B. 最后消息处理
        lastMessageBuilder(c, lastMsg);

        // C. 未读数挂载
        c.setUnreadCount(unreadCount != null ? unreadCount : 0);

        // D. 成员处理与在线人数统计
        if (members != null) {
            List<ChatMemberVO> memberVOList = new ArrayList<>();
            int onlineCount = 0;

            for (ChatMember m : members) {
                boolean isOnline = m.getUserId() != null && onlineUsers.containsKey(m.getUserId());
                if (isOnline) onlineCount++;

                chatMemberVOBuilder(memberVOList, m, isOnline);
            }

            c.setOnLineMemberCount(onlineCount);
            c.setChatMembers(memberVOList);
        }
    }

    private void chatMemberVOBuilder(List<ChatMemberVO> memberVOList, ChatMember m, boolean isOnline) {
        ChatMemberVO vo = new ChatMemberVO();
        vo.setUid(m.getUid());
        vo.setNickname(m.getNickname());
        vo.setOnline(isOnline);
        vo.setLastSeenAt(m.getLastSeenAt());

        if (m.getAvatarObjectName() != null) {
            vo.setAvatar(ImageUtils.buildImage(m.getAvatarObjectName(), minioOSSOperator));
        }

        memberVOList.add(vo);
    }

    /**
     * 将 ChatMember 列表转换为 ChatMemberVO 列表（包含头像 URL）
     */
    private List<ChatMemberVO> toChatMemberVOList(List<ChatMember> members, Map<Integer, Session> onlineUsers) {
        if (members == null || members.isEmpty()) return Collections.emptyList();
        List<ChatMemberVO> memberVOList = new ArrayList<>();
        for (ChatMember m : members) {
            boolean isOnline = m.getUserId() != null && onlineUsers.containsKey(m.getUserId());
            chatMemberVOBuilder(memberVOList, m, isOnline);
        }
        return memberVOList;
    }

    // ============================================================
    // 2. 业务初始化逻辑 (App Initialization)
    // ============================================================

    @Override
    public AppInitVO getChatVOListAndMemberStatusList(Integer userId) {
        List<ChatVO> chatVOList = chatMapper.getChatVOListByUserId(userId);
        if (chatVOList == null || chatVOList.isEmpty()) return null;

        Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();
        List<ChatMember> allMembers = chatMemberMapper.getChatMemberListByUserId(userId);

        Map<String, List<ChatMember>> chatMemberMap = allMembers.stream()
                .collect(Collectors.groupingBy(ChatMember::getChatCode));

        Map<String, Integer> unreadCountMap;
        unreadCountMap = messageMapper.getUnreadCountMapByUserId(userId).stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("chatCode"),
                        row -> ((Number) row.get("unreadCount")).intValue(),
                        (v1, v2) -> v1));

        Map<String, MessageVO> lastMessageMap = messageMapper.getLastMessageListByUserId(userId);

        Map<String, UserStatus> uniqueUserStatusMap = new HashMap<>();
        for (ChatMember m : allMembers) {
            m.setChatId(null);
            uniqueUserStatusMap.putIfAbsent(m.getUid(), new UserStatus(
                    m.getUid(), onlineUsers.containsKey(m.getUserId()), m.getLastSeenAt()));
        }

        for (ChatVO c : chatVOList) {
            chatVOBuilder(c,
                    onlineUsers,
                    lastMessageMap.get(c.getChatCode()),
                    unreadCountMap.get(c.getChatCode()),
                    chatMemberMap.get(c.getChatCode()));
        }

        return new AppInitVO(chatVOList, new ArrayList<>(uniqueUserStatusMap.values()));
    }

    @Override
    public AppInitVO getChatVOListAndMemberStatusListLite(Integer userId) {
        List<ChatVO> chatVOList = chatMapper.getChatVOListByUserId(userId);
        if (chatVOList == null || chatVOList.isEmpty()) {
            return new AppInitVO(Collections.emptyList(), Collections.emptyList());
        }

        Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();
        List<ChatMemberLite> liteMembers = chatMemberMapper.getChatMemberLiteListByUserId(userId);

        Map<String, Integer> onlineCountMap = new HashMap<>();
        Map<String, UserStatus> uniqueUserStatusMap = new HashMap<>();

        for (ChatMemberLite m : liteMembers) {
            boolean isOnline = m.getUserId() != null && onlineUsers.containsKey(m.getUserId());
            onlineCountMap.merge(m.getChatCode(), isOnline ? 1 : 0, Integer::sum);
            uniqueUserStatusMap.putIfAbsent(
                    m.getUid(),
                    new UserStatus(m.getUid(), isOnline, m.getLastSeenAt())
            );
        }

        Map<String, Integer> unreadCountMap = messageMapper.getUnreadCountMapByUserId(userId).stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("chatCode"),
                        row -> ((Number) row.get("unreadCount")).intValue(),
                        (v1, v2) -> v1));
        Map<String, MessageVO> lastMessageMap = messageMapper.getLastMessageListByUserId(userId);

        for (ChatVO c : chatVOList) {
            if (c.getAvatarObjectName() != null) {
                c.setAvatar(ImageUtils.buildImage(c.getAvatarObjectName(), minioOSSOperator));
                c.setAvatarObjectName(null);
            }

            MessageVO lastMsg = lastMessageMap.get(c.getChatCode());
            lastMessageBuilder(c, lastMsg);

            c.setUnreadCount(unreadCountMap.getOrDefault(c.getChatCode(), 0));
            c.setOnLineMemberCount(onlineCountMap.getOrDefault(c.getChatCode(), 0));
            c.setChatMembers(null);
        }

        return new AppInitVO(chatVOList, new ArrayList<>(uniqueUserStatusMap.values()));
    }

    private void lastMessageBuilder(ChatVO c, MessageVO lastMsg) {
        if (lastMsg != null) {
            String objectIdsJson = lastMsg.getObjectIds();
            if (objectIdsJson != null && !objectIdsJson.isEmpty()) {
                try {
                    List<Integer> objectIds = objectMapper.readValue(objectIdsJson, new TypeReference<List<Integer>>() {});
                    List<Image> images = new ArrayList<>();
                    for (Integer objectId : objectIds) {
                        FileEntity objectEntity = fileService.findById(objectId);
                        if (objectEntity != null) {
                            Image image = ImageUtils.buildImage(objectEntity.getObjectName(), minioOSSOperator);
                            if (image != null) {
                                image.setObjectId(objectId);
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
            lastMsg.setObjectIds(null);
            c.setLastMessage(lastMsg);
            c.setLastActiveAt(lastMsg.getCreateTime());
        } else {
            c.setLastActiveAt(c.getCreateTime());
        }
    }

    // ============================================================
    // 3. 房间详情与权限逻辑 (Chat Detail & Security)
    // ============================================================

    @Override
    public ChatVO getChat(Integer userId, String chatCode) {
        Integer chatId = getChatId(userId, chatCode);
        ChatVO chatVO = chatMapper.getChatVOByChatId(chatId);
        if (chatVO != null) {
            Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();
            List<ChatMember> members = chatMemberMapper.getChatMemberListByChatId(chatId);
            MessageVO lastMsg = messageMapper.getLastMessageByChatId(chatId);
            Integer unreadCount = messageMapper.getUnreadCountMapByUserIdAndChatId(userId, chatId);
            chatVOBuilder(chatVO, onlineUsers, lastMsg, unreadCount, members);
        }
        return chatVO;
    }

    @Override
    public List<ChatMemberVO> getChatMemberVOList(Integer userId, String chatCode) {
        Integer chatId = getChatId(userId, chatCode);
        Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();
        List<ChatMember> members = chatMemberMapper.getChatMemberListByChatId(chatId);
        return toChatMemberVOList(members, onlineUsers);
    }

    @Override
    public Integer getChatId(Integer userId, String chatCode) {
        Integer chatId = chatMapper.getChatIdByChatCode(chatCode);
        if (chatId == null) {
            log.warn("[非法请求] 用户 {} 尝试访问不存在的 chatCode: {}", userId, chatCode);
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND);
        }
        if (!chatMapper.isValidChatId(userId, chatId)) {
            log.warn("[权限拒绝] 用户 {} 尝试访问未加入的聊天室: {}", userId, chatCode);
            throw new BusinessException(ErrorCode.NOT_A_MEMBER);
        }
        return chatId;
    }

    @Override
    public ChatJoinInfo getJoinInfo(Integer chatId) {
        return chatMapper.getJoinInfoByChatId(chatId);
    }

    @Override
    public ChatVO validateChatJoin(ValidateChatJoinReq req) {
        Integer chatId = null;
        if (req.getInviteCode() != null && !req.getInviteCode().isBlank()) {
            chatId = chatMapper.getChatIdByInviteCodeHash(InviteCodeUtils.sha256Hex(req.getInviteCode()));
        } else if (req.getChatCode() != null && !req.getChatCode().isBlank()) {
            if (req.getPassword() == null || req.getPassword().isEmpty()) {
                throw new BusinessException(ErrorCode.PASSWORD_REQUIRED, "Password is required when chatCode is provided");
            }
            chatId = chatMapper.getChatIdByChatCode(req.getChatCode());

        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Either inviteCode or chatCode must be provided");
        }
        if (chatId == null) {
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND, "Chat room not found by inviteCode or chatCode");
        }
        ChatJoinInfo info = chatMapper.getJoinInfoByChatId(chatId);
        if (info == null || info.getChatId() == null) {
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND, "Chat room not found");
        }
        if (info.getJoinEnabled() == null || info.getJoinEnabled() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Join is disabled for this chat room");
        }
        if (req.getChatCode() != null && !req.getChatCode().isBlank()) {
            if (info.getChatPasswordHash() == null || info.getChatPasswordHash().isBlank()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Password login is not enabled for this chat room");
            }
            if (!PasswordUtils.matches(req.getPassword(), info.getChatPasswordHash())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Incorrect password");
            }
        }
        ChatVO chatVO = chatMapper.getChatVOByChatId(info.getChatId());
        if (chatVO == null) {
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND, "Chat room not found");
        }

        if (chatVO.getAvatarObjectName() != null) {
            chatVO.setAvatar(ImageUtils.buildImage(chatVO.getAvatarObjectName(), minioOSSOperator));
        }
        chatVO.setAvatarObjectName(null);

        chatVO.setOwnerUid(null);
        chatVO.setJoinEnabled(null);
        chatVO.setLastActiveAt(null);
        chatVO.setCreateTime(null);
        chatVO.setUpdateTime(null);
        chatVO.setUnreadCount(null);
        chatVO.setLastMessage(null);
        chatVO.setOnLineMemberCount(null);
        chatVO.setChatMembers(null);

        return chatVO;
    }

    // ============================================================
    // 4. 其他逻辑
    // ============================================================

    @Override
    public List<Integer> getChatMembers(Integer userId) {
        return chatMemberMapper.getChatMembersById(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Chat join(JoinChatReq joinChatReq) {
        if (joinChatReq == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        
        if (joinChatReq.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户ID不能为空");
        }

        // 1. 参数校验
        validateJoinRequest(joinChatReq);

        ChatJoinInfo chatInfo;

        // 2. 分策略处理 (邀请码模式优先，或者密码模式)
        if (joinChatReq.getInviteCode() != null) {
            chatInfo = handleInviteJoin(joinChatReq.getInviteCode());
        } else {
            chatInfo = handlePasswordJoin(joinChatReq.getChatCode(), joinChatReq.getPassword());
        }

        // 3. 全局检查：是否允许加入
        if (Integer.valueOf(0).equals(chatInfo.getJoinEnabled())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该聊天室已禁止加入");
        }

        // 4. 执行加入
        LocalDateTime now = LocalDateTime.now();
        chatMemberMapper.add(chatInfo.getChatId(), joinChatReq.getUserId(), now);

        log.info("用户加入聊天室成功: userId={}, chatId={}", joinChatReq.getUserId(), chatInfo.getChatId());

        // 5. 构建返回对象
        Chat chat = new Chat();
        chat.setId(chatInfo.getChatId());
        chat.setChatCode(chatInfo.getChatCode());
        chat.setJoinEnabled(chatInfo.getJoinEnabled());
        chat.setCreateTime(now);
        chat.setUpdateTime(now);
        return chat;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CreateChatVO createChat(Integer userId, ChatReq chatReq) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "User is required");
        if (chatReq == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Request body is required");
        if (chatReq.getChatName() == null || chatReq.getChatName().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "chatName is required");
        }

        int joinEnabled = chatReq.getJoinEnable() == null ? 1 : chatReq.getJoinEnable();
        String passwordHash = null;
        String password = chatReq.getPassword();
        String confirm = chatReq.getPasswordConfirm();
        if (password != null && !password.isBlank()) {
            if (!password.equals(confirm)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Password confirm mismatch");
            }
            passwordHash = PasswordUtils.encode(password);
        }

        Integer objectId = null;
        if (chatReq.getAvatar() != null) {
            if (chatReq.getAvatar().getObjectId() != null) {
                objectId = chatReq.getAvatar().getObjectId();
            } else if (chatReq.getAvatar().getObjectName() != null) {
                FileEntity existingObject = fileService.findByObjectName(chatReq.getAvatar().getObjectName());
                if (existingObject != null) {
                    objectId = existingObject.getId();
                } else {
                    log.warn("Chat avatar object not found in objects table: {}", chatReq.getAvatar().getObjectName());
                }
            }
        }

        String chatCode = null;
        ChatCreate chatCreate = new ChatCreate();
        chatCreate.setChatName(chatReq.getChatName());
        chatCreate.setOwnerId(userId);
        chatCreate.setJoinEnabled(joinEnabled);
        chatCreate.setChatPasswordHash(passwordHash);
        chatCreate.setObjectId(objectId);

        for (int i = 1; i <= 5; i++) {
            chatCode = UidGenerator.generateUid(8);
            chatCreate.setChatCode(chatCode);
            try {
                chatMapper.insertChat(chatCreate);
                break;
            } catch (DuplicateKeyException e) {
                if (i == 5) throw new BusinessException(ErrorCode.DATABASE_ERROR, "Failed to generate unique chatCode");
            }
        }

        Integer chatId = chatCreate.getId();
        if (chatId == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Create chat failed (no chatId)");
        }

        LocalDateTime now = LocalDateTime.now();
        chatMemberMapper.add(chatId, userId, now);

        int expiryMinutes = chatReq.getJoinLinkExpiryMinutes() == null ? 10080 : chatReq.getJoinLinkExpiryMinutes();
        LocalDateTime expiresAt = now.plusMinutes(Math.max(1, expiryMinutes));
        int maxUses = chatReq.getMaxUses() == null ? 0 : chatReq.getMaxUses();

        if (maxUses != 0 && maxUses != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "maxUses must be 0 or 1");
        }

        String inviteCode = InviteCodeUtils.generateInviteCode(18);
        String codeHash = InviteCodeUtils.sha256Hex(inviteCode);
        ChatInvite invite = new ChatInvite(
                null,
                chatId,
                codeHash,
                expiresAt,
                maxUses,
                0,
                0,
                userId,
                null,
                null
        );
        chatInviteMapper.insert(invite);

        return new CreateChatVO(chatCode, inviteCode);
    }

    // ============================================================
    // 5. 私有辅助方法 (Private Helper Methods)
    // ============================================================

    /**
     * 校验加入请求的基本参数合法性
     */
    private void validateJoinRequest(JoinChatReq req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        boolean hasPwdArgs = req.getChatCode() != null && req.getPassword() != null;
        boolean hasInviteArgs = req.getInviteCode() != null;

        if (hasPwdArgs && hasInviteArgs) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能同时使用密码和邀请码");
        }
        if (!hasPwdArgs && !hasInviteArgs) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数缺失：需提供(ChatCode+密码)或(邀请码)");
        }
    }

    /**
     * 处理密码模式加入
     */
    private ChatJoinInfo handlePasswordJoin(String chatCode, String password) {
        // 建议 Mapper 增加 getJoinInfoByChatCode
        Integer chatId = chatMapper.getChatIdByChatCode(chatCode);
        if (chatId == null) {
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND, "Chat room not found");
        }

        ChatJoinInfo info = chatMapper.getJoinInfoByChatId(chatId);
        if (info == null || info.getChatId() == null) {
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND);
        }

        // 验证密码
        String storedHash = info.getChatPasswordHash();
        if (storedHash == null || storedHash.isBlank()) {
            // 业务规则：如果房间未设置密码哈希，则禁止通过密码模式加入（仅允许邀请）
            throw new BusinessException(ErrorCode.FORBIDDEN, "该房间未开启密码登录");
        }

        if (password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码不能为空");
        }

        if (!PasswordUtils.matches(password, storedHash)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码错误");
        }

        return info;
    }

    /**
     * 处理邀请码模式加入
     * 业务规则：
     * 1. maxUses == 0 -> 无限使用，仅累加计数
     * 2. maxUses == 1 -> 一次性使用，使用成功后立即物理删除（阅后即焚）
     */
    private ChatJoinInfo handleInviteJoin(String inviteCode) {
        String hash = InviteCodeUtils.sha256Hex(inviteCode);
        ChatInvite invite = chatInviteMapper.findByCodeHash(hash);

        // 1. 基础校验
        if (invite == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的邀请码");
        }
        if (Integer.valueOf(1).equals(invite.getRevoked())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邀请码已被撤销");
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邀请码已过期");
        }

        // 2. 剩余次数校验
        // 规则：如果 maxUses > 0 (即为 1)，且 usedCount 已经 >= maxUses，则耗尽
        if (invite.getMaxUses() > 0 && invite.getUsedCount() >= invite.getMaxUses()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邀请码次数已耗尽");
        }

        // 3. 预先获取房间信息 (防止邀请码有效但房间没了)
        ChatJoinInfo info = chatMapper.getJoinInfoByChatId(invite.getChatId());
        if (info == null) {
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND);
        }

        // 4. 消费邀请码 (CAS 原子更新，防止并发超用)
        // SQL 逻辑: UPDATE ... SET used_count = used_count + 1 WHERE hash = ? AND (max_uses = 0 OR used_count < max_uses)
        int rows = chatInviteMapper.consume(invite.getChatId(), hash);
        if (rows <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邀请码失效或并发冲突");
        }

        // 5. 阅后即焚逻辑
        // 如果限制了次数（maxUses > 0，即为 1），且消费成功，则立即物理删除
        if (invite.getMaxUses() > 0) {
            chatInviteMapper.deleteByCodeHash(hash);
            log.info("一次性邀请码已使用并删除: codeHash={}, chatId={}", hash, invite.getChatId());
        }

        return info;
    }

}