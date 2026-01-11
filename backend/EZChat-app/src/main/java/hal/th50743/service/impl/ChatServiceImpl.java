package hal.th50743.service.impl;

import hal.th50743.assembler.ChatAssembler;
import hal.th50743.assembler.ChatMemberAssembler;
import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.mapper.ChatInviteMapper;
import hal.th50743.mapper.ChatMapper;
import hal.th50743.mapper.ChatMemberMapper;
import hal.th50743.mapper.MessageMapper;
import hal.th50743.mapper.UserMapper;
import hal.th50743.pojo.*;
import hal.th50743.utils.*;
import hal.th50743.service.ChatService;
import hal.th50743.service.AssetService;
import hal.th50743.service.ChatInviteService;
import hal.th50743.ws.WebSocketServer;
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
 * 聊天室服务实现类
 * <p>
 * 负责聊天室的创建、查询、加入、退出、解散等核心业务逻辑。
 * <p>
 * 主要功能：
 * <ul>
 *     <li>聊天室 CRUD 操作</li>
 *     <li>成员管理（加入、退出、踢出、转让群主）</li>
 *     <li>密码管理与邀请码验证</li>
 *     <li>私聊房间创建与复用</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMapper chatMapper;
    private final ChatMemberMapper chatMemberMapper;
    private final ChatInviteMapper chatInviteMapper;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final AssetService assetService;
    private final ChatInviteService chatInviteService;
    private final ChatAssembler chatAssembler;
    private final ChatMemberAssembler chatMemberAssembler;

    @Override
    public AppInitVO getChatVOListAndMemberStatusListLite(Integer userId) {
        List<ChatVO> chatVOList = chatMapper.selectChatVOListByUserId(userId);
        if (chatVOList == null || chatVOList.isEmpty()) {
            return new AppInitVO(Collections.emptyList(), Collections.emptyList());
        }

        Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();
        List<ChatMemberLite> liteMembers = chatMemberMapper.selectChatMemberLiteListByUserId(userId);

        Map<String, Integer> onlineCountMap = new HashMap<>();
        Map<String, UserStatus> uniqueUserStatusMap = new HashMap<>();

        for (ChatMemberLite m : liteMembers) {
            boolean isOnline = chatMemberAssembler.isUserOnline(m.getUserId(), onlineUsers);
            onlineCountMap.merge(m.getChatCode(), isOnline ? 1 : 0, (a, b) -> a + b);
            uniqueUserStatusMap.putIfAbsent(
                    m.getUid(),
                    new UserStatus(m.getUid(), isOnline, m.getLastSeenAt()));
        }

        Map<String, Integer> unreadCountMap = getUnreadCountMap(userId);
        Map<String, MessageVO> lastMessageMap = messageMapper.selectLastMessageListByUserId(userId);

        for (ChatVO c : chatVOList) {
            chatAssembler.assembleLite(c,
                    lastMessageMap.get(c.getChatCode()),
                    unreadCountMap.getOrDefault(c.getChatCode(), 0),
                    onlineCountMap.getOrDefault(c.getChatCode(), 0));
        }

        return new AppInitVO(chatVOList, new ArrayList<>(uniqueUserStatusMap.values()));
    }

    @Override
    public ChatVO getChat(Integer userId, String chatCode) {
        Integer chatId = getChatId(userId, chatCode);
        ChatVO chatVO = chatMapper.selectChatVOByChatId(chatId);

        if (chatVO != null) {
            Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();
            List<ChatMember> members = chatMemberMapper.selectChatMemberListByChatId(chatId);
            MessageVO lastMsg = messageMapper.selectLastMessageByChatId(chatId);
            Integer unreadCount = messageMapper.selectUnreadCountMapByUserIdAndChatId(userId, chatId);

            chatAssembler.assemble(chatVO, onlineUsers, lastMsg, unreadCount, members);

            if (Integer.valueOf(1).equals(chatVO.getType()) && chatVO.getChatMembers() != null) {
                User currentUser = userMapper.selectUserById(userId);
                if (currentUser != null) {
                    for (ChatMemberVO member : chatVO.getChatMembers()) {
                        if (!member.getUid().equals(currentUser.getUid())) {
                            chatVO.setChatName(member.getNickname());
                            chatVO.setAvatar(member.getAvatar());
                            break;
                        }
                    }
                }
            }
        }

        return chatVO;
    }

    @Override
    public List<ChatMemberVO> getChatMemberVOList(Integer userId, String chatCode) {
        Integer chatId = getChatId(userId, chatCode);
        Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();
        List<ChatMember> members = chatMemberMapper.selectChatMemberListByChatId(chatId);
        return chatMemberAssembler.toChatMemberVOList(members, onlineUsers);
    }

    @Override
    public Integer getChatId(Integer userId, String chatCode) {
        Integer chatId = chatMapper.selectChatIdByChatCode(chatCode);
        if (chatId == null) {
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND);
        }
        if (!chatMapper.isValidChatId(userId, chatId)) {
            throw new BusinessException(ErrorCode.NOT_A_MEMBER);
        }
        return chatId;
    }

    @Override
    public ChatJoinInfo getJoinInfo(Integer chatId) {
        return chatMapper.selectJoinInfoByChatId(chatId);
    }

    @Override
    public ChatVO validateChatJoin(ValidateChatJoinReq req) {
        Integer chatId = null;

        if (req.getInviteCode() != null && !req.getInviteCode().isBlank()) {
            String hash = InviteCodeUtils.sha256Hex(req.getInviteCode());
            ChatInvite chatInvite = chatInviteMapper.selectByCodeHash(hash);

            if (chatInvite == null) throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
            if (chatInvite.getExpiresAt() != null && chatInvite.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.INVITE_CODE_EXPIRED);
            }
            if (chatInvite.getRevoked() != null && chatInvite.getRevoked() == 1) {
                throw new BusinessException(ErrorCode.INVITE_CODE_REVOKED);
            }
            if (chatInvite.getMaxUses() > 0 && chatInvite.getUsedCount() >= chatInvite.getMaxUses()) {
                throw new BusinessException(ErrorCode.INVITE_CODE_USAGE_LIMIT_REACHED);
            }

            chatId = chatInvite.getChatId();
        } else if (req.getChatCode() != null && !req.getChatCode().isBlank()) {
            if (req.getPassword() == null || req.getPassword().isEmpty()) {
                throw new BusinessException(ErrorCode.PASSWORD_REQUIRED);
            }
            chatId = chatMapper.selectChatIdByChatCode(req.getChatCode());
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        if (chatId == null) throw new BusinessException(ErrorCode.CHAT_NOT_FOUND);

        ChatJoinInfo info = chatMapper.selectJoinInfoByChatId(chatId);
        if (info == null) throw new BusinessException(ErrorCode.CHAT_NOT_FOUND);

        if (info.getJoinEnabled() == null || info.getJoinEnabled() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (req.getChatCode() != null) {
            if (info.getChatPasswordHash() == null) throw new BusinessException(ErrorCode.FORBIDDEN);
            if (!PasswordUtils.matches(req.getPassword(), info.getChatPasswordHash())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
        }

        ChatVO chatMapperChatVO = chatMapper.selectChatVOByChatId(info.getChatId());
        chatAssembler.assembleLite(chatMapperChatVO, null, null, null);
        chatAssembler.sanitizeForPublic(chatMapperChatVO);
        return chatMapperChatVO;
    }

    @Override
    public List<Integer> getChatMembers(Integer userId) {
        return chatMemberMapper.selectChatMembersById(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Chat join(JoinChatReq joinChatReq) {
        if (joinChatReq == null || joinChatReq.getUserId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        ChatJoinInfo chatInfo;
        if (joinChatReq.getInviteCode() != null) {
            chatInfo = handleInviteJoin(joinChatReq.getInviteCode());
        } else {
            chatInfo = handlePasswordJoin(joinChatReq.getChatCode(), joinChatReq.getPassword());
        }

        if (Integer.valueOf(0).equals(chatInfo.getJoinEnabled())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        LocalDateTime now = LocalDateTime.now();
        chatMemberMapper.insertChatMember(chatInfo.getChatId(), joinChatReq.getUserId(), now);

        User user = userMapper.selectUserById(joinChatReq.getUserId());
        String displayName = (user.getUsername() == null ? "[Guest] " : "") + user.getNickname();

        messageMapper.updateChatSequence(chatInfo.getChatId());
        Long seqId = messageMapper.selectCurrentSequence(chatInfo.getChatId());

        Message sysMsg = new Message();
        sysMsg.setChatId(chatInfo.getChatId());
        sysMsg.setSenderId(joinChatReq.getUserId());
        sysMsg.setSeqId(seqId);
        sysMsg.setType(11);
        sysMsg.setText(displayName);
        sysMsg.setAssetIds("");
        sysMsg.setCreateTime(now);
        sysMsg.setUpdateTime(now);
        messageMapper.insertMessage(sysMsg);

        List<Integer> memberIds = chatMemberMapper.selectChatMemberListByChatId(chatInfo.getChatId())
                .stream().map(ChatMember::getUserId).collect(Collectors.toList());

        JoinBroadcastVO broadcastVO = chatMemberAssembler.toJoinBroadcastVO(user, chatInfo.getChatCode(), displayName, now);
        broadcastVO.setSeqId(seqId);
        WebSocketServer.broadcast(MessageUtils.setMessage(3001, "MEMBER_JOIN", broadcastVO), memberIds);

        Chat chat = new Chat();
        chat.setId(chatInfo.getChatId());
        chat.setChatCode(chatInfo.getChatCode());
        return chat;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CreateChatVO createChat(Integer userId, ChatReq chatReq) {
        String chatCode = null;
        ChatCreate chatCreate = new ChatCreate();
        chatCreate.setChatName(chatReq.getChatName());
        chatCreate.setOwnerId(userId);
        chatCreate.setJoinEnabled(chatReq.getJoinEnable());
        chatCreate.setObjectId(chatReq.getAvatar() != null ? chatReq.getAvatar().getAssetId() : null);
        chatCreate.setMaxMembers(200);
        
        if (chatReq.getPassword() != null) {
            chatCreate.setChatPasswordHash(PasswordUtils.encode(chatReq.getPassword()));
        }

        for (int i = 1; i <= 5; i++) {
            chatCode = UidGenerator.generateUid(8);
            chatCreate.setChatCode(chatCode);
            try {
                chatMapper.insertChat(chatCreate);
                break;
            } catch (DuplicateKeyException e) {
                if (i == 5) throw new BusinessException(ErrorCode.DATABASE_ERROR);
            }
        }

        Integer chatId = chatCreate.getId();
        LocalDateTime now = LocalDateTime.now();
        chatMemberMapper.insertChatMember(chatId, userId, now);

        ChatInviteVO invite = chatInviteService.createInviteForChatId(
                userId, chatId, chatReq.getJoinLinkExpiryMinutes(), chatReq.getMaxUses());

        messageMapper.updateChatSequence(chatId);
        Message sysMsg = new Message();
        sysMsg.setChatId(chatId);
        sysMsg.setSenderId(userId);
        sysMsg.setSeqId(messageMapper.selectCurrentSequence(chatId));
        sysMsg.setType(10);
        sysMsg.setCreateTime(now);
        sysMsg.setUpdateTime(now);
        messageMapper.insertMessage(sysMsg);

        return new CreateChatVO(chatCode, invite.inviteCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveChat(Integer userId, String chatCode) {
        Integer chatId = getChatId(userId, chatCode);
        Integer ownerId = chatMapper.selectOwnerIdByChatId(chatId);
        if (ownerId.equals(userId)) {
            // Logic to transfer owner or disband
            // For simplicity, simple leave not allowed for owner if members exist
            int count = chatMemberMapper.countMembersByChatId(chatId);
            if (count > 1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Owner cannot leave without transferring ownership");
            } else {
                disbandChat(userId, chatCode);
                return;
            }
        }
        chatMemberMapper.deleteChatMember(chatId, userId);
        // Broadcast leave message... (Simplified)
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disbandChat(Integer userId, String chatCode) {
        Integer chatId = getChatId(userId, chatCode);
        Integer ownerId = chatMapper.selectOwnerIdByChatId(chatId);
        if (!ownerId.equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        
        chatMemberMapper.deleteChatMembersByChatId(chatId);
        chatMapper.deleteChatById(chatId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateChatPassword(Integer userId, String chatCode, ChatPasswordUpdateReq req) {
        Integer chatId = getChatId(userId, chatCode);
        Integer ownerId = chatMapper.selectOwnerIdByChatId(chatId);
        if (!ownerId.equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        
        String hash = (req.getPassword() == null || req.getPassword().isEmpty()) 
                ? null : PasswordUtils.encode(req.getPassword());
        chatMapper.updateChatPassword(chatId, hash);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatVO updateChatBasicInfo(Integer userId, String chatCode, ChatBasicUpdateReq req) {
        Integer chatId = getChatId(userId, chatCode);
        Integer ownerId = chatMapper.selectOwnerIdByChatId(chatId);
        if (!ownerId.equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        
        chatMapper.updateChatBasicInfo(chatId, req.getChatName(), req.getMaxMembers(), req.getAnnouncement(), req.getAvatar() != null ? req.getAvatar().getAssetId() : null);
        return getChat(userId, chatCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void kickMembers(Integer userId, String chatCode, ChatKickReq req) {
        Integer chatId = getChatId(userId, chatCode);
        Integer ownerId = chatMapper.selectOwnerIdByChatId(chatId);
        if (!ownerId.equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        
        // Convert uids to userIds
        List<Integer> targetIds = new ArrayList<>();
        for (String uid : req.getMemberUids()) {
            Integer uidId = Integer.valueOf(userMapper.selectIdByUid(uid).toString());
            targetIds.add(uidId);
        }
        chatMemberMapper.deleteChatMembersByChatIdAndUserIds(chatId, targetIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferOwner(Integer userId, String chatCode, ChatOwnerTransferReq req) {
        Integer chatId = getChatId(userId, chatCode);
        Integer ownerId = chatMapper.selectOwnerIdByChatId(chatId);
        if (!ownerId.equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        
        Integer newOwnerId = Integer.valueOf(userMapper.selectIdByUid(req.getNewOwnerUid()).toString());
        chatMapper.updateChatOwner(chatId, newOwnerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createPrivateChat(Integer userId, Integer targetUserId) {
        String existingCode = chatMapper.selectPrivateChatCodeBetweenUsers(userId, targetUserId);
        if (existingCode != null) return existingCode;

        String chatCode = null;
        ChatCreate chatCreate = new ChatCreate();
        chatCreate.setChatName("Private Chat");
        chatCreate.setOwnerId(userId);
        chatCreate.setJoinEnabled(0);
        chatCreate.setMaxMembers(2);
        chatCreate.setType(1);
        
        for (int i = 1; i <= 5; i++) {
            chatCode = UidGenerator.generateUid(8);
            chatCreate.setChatCode(chatCode);
            try {
                chatMapper.insertChat(chatCreate);
                break;
            } catch (DuplicateKeyException e) {
                if (i == 5) throw new BusinessException(ErrorCode.DATABASE_ERROR);
            }
        }
        
        LocalDateTime now = LocalDateTime.now();
        chatMemberMapper.insertChatMember(chatCreate.getId(), userId, now);
        chatMemberMapper.insertChatMember(chatCreate.getId(), targetUserId, now);
        
        return chatCode;
    }

    private Map<String, Integer> getUnreadCountMap(Integer userId) {
        return messageMapper.selectUnreadCountMapByUserId(userId).stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("chatCode"),
                        row -> ((Number) row.get("unreadCount")).intValue(),
                        (v1, v2) -> v1));
    }

    private ChatJoinInfo handlePasswordJoin(String chatCode, String password) {
        Integer chatId = chatMapper.selectChatIdByChatCode(chatCode);
        if (chatId == null) throw new BusinessException(ErrorCode.CHAT_NOT_FOUND);
        ChatJoinInfo info = chatMapper.selectJoinInfoByChatId(chatId);
        if (info.getChatPasswordHash() == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (!PasswordUtils.matches(password, info.getChatPasswordHash())) throw new BusinessException(ErrorCode.BAD_REQUEST);
        return info;
    }

    private ChatJoinInfo handleInviteJoin(String inviteCode) {
        String hash = InviteCodeUtils.sha256Hex(inviteCode);
        ChatInvite chatInvite = chatInviteMapper.selectByCodeHash(hash);
        if (chatInvite == null) throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        
        chatInviteService.consumeInvite(chatInvite.getChatId(), chatInvite.getCodeHash());
        return chatMapper.selectJoinInfoByChatId(chatInvite.getChatId());
    }
}
