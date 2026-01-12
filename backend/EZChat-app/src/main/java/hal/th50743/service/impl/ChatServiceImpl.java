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
 * 聊天室服务实现类 - 聊天室核心业务逻辑的统一入口
 *
 * <h3>职责概述</h3>
 * <p>
 * 负责聊天室全生命周期管理，包括创建、查询、加入、退出、解散等操作。
 * 同时处理成员关系、权限验证和实时状态同步。
 * </p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li><b>聊天室 CRUD</b>：创建群聊/私聊、更新信息、解散群聊</li>
 *     <li><b>成员管理</b>：加入、退出、踢出成员、转让群主</li>
 *     <li><b>访问控制</b>：密码验证、邀请码验证、权限检查</li>
 *     <li><b>私聊房间</b>：自动创建与复用机制</li>
 * </ul>
 *
 * <h3>调用路径</h3>
 * <ul>
 *     <li>{@code ChatController} → 本服务：HTTP 请求入口</li>
 *     <li>{@code AuthController} → 本服务：访客加入验证</li>
 *     <li>{@code WebSocketServer} → 本服务：获取成员列表用于广播</li>
 * </ul>
 *
 * <h3>核心不变量</h3>
 * <ul>
 *     <li>用户必须是聊天室成员才能访问（通过 {@link #getChatId(Integer, String)} 验证）</li>
 *     <li>群主操作（踢人、解散、转让）需要 ownerId 验证</li>
 *     <li>私聊房间（type=1）固定 2 人，禁止加入</li>
 * </ul>
 *
 * <h3>外部依赖</h3>
 * <ul>
 *     <li><b>WebSocketServer</b>：成员变更时广播通知</li>
 *     <li><b>MessageMapper</b>：插入系统消息（加入/退出通知）</li>
 *     <li><b>ChatInviteService</b>：邀请码的创建与消费</li>
 * </ul>
 *
 * <h3>关键错误码</h3>
 * <table border="1">
 *     <tr><td>CHAT_NOT_FOUND</td><td>聊天室不存在</td></tr>
 *     <tr><td>NOT_A_MEMBER</td><td>用户不是成员</td></tr>
 *     <tr><td>FORBIDDEN</td><td>无权限操作（非群主）</td></tr>
 *     <tr><td>INVITE_CODE_INVALID/EXPIRED/REVOKED</td><td>邀请码无效</td></tr>
 *     <tr><td>PASSWORD_REQUIRED</td><td>需要密码加入</td></tr>
 * </table>
 *
 * @author 系统开发者
 * @since 1.0
 * @see ChatController
 * @see WebSocketServer
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

    /**
     * 获取用户的聊天室列表和成员在线状态（轻量版）
     *
     * <p>用于应用初始化时一次性拉取侧边栏所需的全部数据，减少 HTTP 往返。</p>
     *
     * <h4>返回数据</h4>
     * <ul>
     *     <li>chatVOList：用户所有聊天室的摘要信息（最后消息、未读数、在线人数）</li>
     *     <li>userStatusList：去重后的用户在线状态列表</li>
     * </ul>
     *
     * @param userId 当前登录用户的数据库 ID，不可为 null
     * @return 包含聊天室列表和用户状态的初始化数据；若用户无聊天室则返回空列表
     */
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

    /**
     * 获取聊天室详细信息
     *
     * <p>进入聊天室时调用，返回完整的聊天室信息（含成员列表、在线状态）。</p>
     *
     * <h4>私聊特殊处理</h4>
     * <p>type=1（私聊）时，用对方的昵称和头像覆盖聊天室名称和头像。</p>
     *
     * @param userId   当前用户 ID
     * @param chatCode 聊天室对外唯一标识（8位字符串）
     * @return 聊天室详情 VO，包含成员列表和在线人数；若不存在返回 null
     * @throws BusinessException CHAT_NOT_FOUND 或 NOT_A_MEMBER
     */
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

    /**
     * 获取聊天室成员列表（带在线状态）
     *
     * @param userId   当前用户 ID（用于权限校验）
     * @param chatCode 聊天室代码
     * @return 成员 VO 列表，包含 uid、昵称、头像、在线状态
     * @throws BusinessException CHAT_NOT_FOUND 或 NOT_A_MEMBER
     */
    @Override
    public List<ChatMemberVO> getChatMemberVOList(Integer userId, String chatCode) {
        Integer chatId = getChatId(userId, chatCode);
        Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();
        List<ChatMember> members = chatMemberMapper.selectChatMemberListByChatId(chatId);
        return chatMemberAssembler.toChatMemberVOList(members, onlineUsers);
    }

    /**
     * 获取聊天室内部 ID 并校验成员资格
     *
     * <p>核心权限校验方法，几乎所有聊天室操作都依赖此方法。</p>
     *
     * @param userId   当前用户 ID
     * @param chatCode 聊天室对外代码
     * @return 聊天室数据库主键 ID
     * @throws BusinessException CHAT_NOT_FOUND - 聊天室不存在
     * @throws BusinessException NOT_A_MEMBER - 用户不是该聊天室成员
     */
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

    /** 获取聊天室加入所需信息（密码哈希、是否允许加入等） */
    @Override
    public ChatJoinInfo getJoinInfo(Integer chatId) {
        return chatMapper.selectJoinInfoByChatId(chatId);
    }

    /**
     * 验证聊天室加入权限（访客/登录用户通用）
     *
     * <p>访客加入流程的第一步：验证密码或邀请码是否有效，返回脱敏后的房间信息。</p>
     *
     * <h4>验证方式（二选一）</h4>
     * <ul>
     *     <li>邀请码：验证有效性、是否过期、是否撤销、是否超过使用次数</li>
     *     <li>密码：验证 chatCode + password 组合</li>
     * </ul>
     *
     * @param req 验证请求，包含 inviteCode 或 (chatCode + password)
     * @return 脱敏后的聊天室信息（不含成员列表和敏感数据）
     * @throws BusinessException INVITE_CODE_INVALID/EXPIRED/REVOKED - 邀请码问题
     * @throws BusinessException PASSWORD_REQUIRED/BAD_REQUEST - 密码问题
     * @throws BusinessException FORBIDDEN - 聊天室禁止加入
     */
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

    /** 获取用户所在的所有聊天室的成员 ID 列表（用于 WebSocket 广播） */
    @Override
    public List<Integer> getChatMembers(Integer userId) {
        return chatMemberMapper.selectChatMembersById(userId);
    }

    /**
     * 加入聊天室
     *
     * <p>用户通过邀请码或密码加入聊天室的核心方法。</p>
     *
     * <h4>执行流程</h4>
     * <ol>
     *     <li>验证加入方式（邀请码/密码）并获取聊天室信息</li>
     *     <li>检查聊天室是否允许加入</li>
     *     <li>插入成员关系记录</li>
     *     <li>生成系统消息（"XXX 加入了聊天室"）</li>
     *     <li>通过 WebSocket 广播给所有成员</li>
     * </ol>
     *
     * <h4>副作用</h4>
     * <ul>
     *     <li>写入 chat_members 表</li>
     *     <li>写入 messages 表（系统消息 type=11）</li>
     *     <li>更新 chat_sequences 表</li>
     *     <li>WebSocket 广播 MEMBER_JOIN 事件</li>
     * </ul>
     *
     * @param joinChatReq 加入请求（含 userId + inviteCode 或 chatCode+password）
     * @return 聊天室基本信息（id + chatCode）
     * @throws BusinessException BAD_REQUEST - 参数无效
     * @throws BusinessException FORBIDDEN - 聊天室禁止加入
     */
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

    /**
     * 创建聊天室
     *
     * <p>创建群聊（type=0），自动生成邀请码，并将创建者加入成员列表。</p>
     *
     * <h4>chatCode 生成策略</h4>
     * <p>使用 8 位随机字符串，若发生唯一键冲突则重试最多 5 次。</p>
     *
     * @param userId  创建者用户 ID
     * @param chatReq 创建请求（名称、密码、头像、邀请码设置等）
     * @return 包含 chatCode 和 inviteCode 的创建结果
     * @throws BusinessException DATABASE_ERROR - chatCode 生成重试失败
     */
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
        chatCreate.setType(0);
        
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

    /**
     * 退出聊天室
     *
     * <p>群主退出时：若仅剩自己则解散群聊，否则抛出异常提示先转让群主。</p>
     *
     * @param userId   当前用户 ID
     * @param chatCode 聊天室代码
     * @throws BusinessException BAD_REQUEST - 群主未转让所有权就退出
     */
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

    /**
     * 解散聊天室（仅群主可操作）
     *
     * @param userId   当前用户 ID
     * @param chatCode 聊天室代码
     * @throws BusinessException FORBIDDEN - 非群主操作
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disbandChat(Integer userId, String chatCode) {
        Integer chatId = getChatId(userId, chatCode);
        Integer ownerId = chatMapper.selectOwnerIdByChatId(chatId);
        if (!ownerId.equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        
        chatMemberMapper.deleteChatMembersByChatId(chatId);
        chatMapper.deleteChatById(chatId);
    }

    /**
     * 更新聊天室密码（仅群主可操作）
     *
     * @param userId   当前用户 ID
     * @param chatCode 聊天室代码
     * @param req      密码更新请求（password 为空则清除密码）
     * @throws BusinessException FORBIDDEN - 非群主操作
     */
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

    /** 更新聊天室基本信息（名称、最大人数、公告、头像），仅群主可操作 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatVO updateChatBasicInfo(Integer userId, String chatCode, ChatBasicUpdateReq req) {
        Integer chatId = getChatId(userId, chatCode);
        Integer ownerId = chatMapper.selectOwnerIdByChatId(chatId);
        if (!ownerId.equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        
        chatMapper.updateChatBasicInfo(chatId, req.getChatName(), req.getMaxMembers(), req.getAnnouncement(), req.getAvatar() != null ? req.getAvatar().getAssetId() : null);
        return getChat(userId, chatCode);
    }

    /** 踢出成员（仅群主可操作），批量移除指定 uid 的成员 */
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

    /** 转让群主（仅当前群主可操作） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferOwner(Integer userId, String chatCode, ChatOwnerTransferReq req) {
        Integer chatId = getChatId(userId, chatCode);
        Integer ownerId = chatMapper.selectOwnerIdByChatId(chatId);
        if (!ownerId.equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        
        Integer newOwnerId = Integer.valueOf(userMapper.selectIdByUid(req.getNewOwnerUid()).toString());
        chatMapper.updateChatOwner(chatId, newOwnerId);
    }

    /**
     * 创建或获取私聊房间
     *
     * <p>基于好友关系的 1v1 私聊。若两人之间已存在私聊房间则复用，否则新建。</p>
     *
     * <h4>私聊房间特性</h4>
     * <ul>
     *     <li>type=1（私聊类型）</li>
     *     <li>maxMembers=2（固定两人）</li>
     *     <li>joinEnabled=0（禁止加入）</li>
     * </ul>
     *
     * @param userId       当前用户 ID
     * @param targetUserId 目标用户（好友）ID
     * @return 私聊房间的 chatCode
     * @throws BusinessException DATABASE_ERROR - chatCode 生成失败
     */
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

    /** 获取用户在各聊天室的未读消息数（chatCode → count） */
    private Map<String, Integer> getUnreadCountMap(Integer userId) {
        return messageMapper.selectUnreadCountMapByUserId(userId).stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("chatCode"),
                        row -> ((Number) row.get("unreadCount")).intValue(),
                        (v1, v2) -> v1));
    }

    /** 密码方式加入：验证 chatCode + password，返回聊天室信息 */
    private ChatJoinInfo handlePasswordJoin(String chatCode, String password) {
        Integer chatId = chatMapper.selectChatIdByChatCode(chatCode);
        if (chatId == null) throw new BusinessException(ErrorCode.CHAT_NOT_FOUND);
        ChatJoinInfo info = chatMapper.selectJoinInfoByChatId(chatId);
        if (info.getChatPasswordHash() == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (!PasswordUtils.matches(password, info.getChatPasswordHash())) throw new BusinessException(ErrorCode.BAD_REQUEST);
        return info;
    }

    /** 邀请码方式加入：验证并消费邀请码，返回聊天室信息 */
    private ChatJoinInfo handleInviteJoin(String inviteCode) {
        String hash = InviteCodeUtils.sha256Hex(inviteCode);
        ChatInvite chatInvite = chatInviteMapper.selectByCodeHash(hash);
        if (chatInvite == null) throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        
        chatInviteService.consumeInvite(chatInvite.getChatId(), chatInvite.getCodeHash());
        return chatMapper.selectJoinInfoByChatId(chatInvite.getChatId());
    }
}
