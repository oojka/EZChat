package hal.th50743.service.impl;

import hal.th50743.assembler.ChatAssembler;
import hal.th50743.exception.BusinessException;
import hal.th50743.exception.ErrorCode;
import hal.th50743.mapper.ChatInviteMapper;
import hal.th50743.mapper.ChatMapper;
import hal.th50743.mapper.ChatMemberMapper;
import hal.th50743.mapper.ChatMemberMapper;
import hal.th50743.mapper.MessageMapper;
import hal.th50743.mapper.UserMapper;
import hal.th50743.pojo.*;

import hal.th50743.utils.*;
import hal.th50743.service.ChatService;
import hal.th50743.service.AssetService;
import hal.th50743.ws.WebSocketServer;

import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ChatServiceImpl - 聊天服务核心实现类
 * 
 * ## 核心职责
 * 1. **应用初始化**：处理用户登录后的聊天室列表、成员状态、未读消息等初始化数据
 * 2. **房间详情与权限**：提供聊天室详情、成员列表、权限验证等核心功能
 * 3. **房间加入逻辑**：处理用户加入聊天室的完整流程，支持密码模式和邀请码模式
 * 4. **房间创建逻辑**：处理新聊天室的创建，包括房间代码生成、邀请码生成等
 * 5. **业务规则执行**：执行各种业务规则验证，如邀请码有效性、密码验证等
 * 
 * ## 架构位置
 * - **服务层**：业务逻辑的核心实现层
 * - **依赖**：依赖多个Mapper（数据访问层）和工具类
 * - **事务管理**：使用Spring的声明式事务管理
 * - **日志记录**：使用Lombok的@Slf4j进行结构化日志记录
 * 
 * ## 设计原则
 * 1. **单一职责**：每个方法专注于一个明确的业务功能
 * 2. **事务边界**：写操作使用@Transactional确保数据一致性
 * 3. **防御式编程**：对所有输入进行验证，防止非法状态
 * 4. **业务规则集中**：将业务规则集中在服务层，便于维护和测试
 * 
 * ## 主要功能模块
 * 1. 应用初始化逻辑（App Initialization）
 * 2. 房间详情与权限逻辑（Chat Detail & Security）
 * 3. 房间加入与创建逻辑（Join & Create）
 * 4. 私有辅助方法（Private Helper Methods）
 * 
 * ## 异常处理策略
 * 1. 使用自定义BusinessException统一处理业务异常
 * 2. 所有异常消息使用英语，便于国际化
 * 3. 错误代码统一管理，便于前端处理
 * 4. 关键操作记录日志，便于问题追踪
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
    private final ChatAssembler chatAssembler;

    // ============================================================
    // 1. 业务初始化逻辑 (App Initialization)
    // ============================================================

    /**
     * 获取轻量版应用初始化数据
     * 
     * ## 功能描述
     * 为用户提供登录后的轻量初始化数据，包括：
     * 1. 用户加入的所有聊天室列表（ChatVO）
     * 2. 每个聊天室的在线成员数量（不包含具体成员信息）
     * 3. 每个聊天室的最后一条消息
     * 4. 每个聊天室的未读消息数量
     * 5. 所有联系人的在线状态
     * 
     * ## 使用场景
     * 1. 应用定期刷新（心跳检测）
     * 2. 需要快速更新的场景
     * 3. 移动端或网络条件较差的环境
     * 
     * ## 性能优势
     * 1. 数据量较小，响应速度快
     * 2. 不包含完整的成员列表，减少数据传输
     * 3. 适用于频繁更新的场景
     * 
     * ## 与完整版的区别
     * 1. 不返回具体的成员列表，只返回在线成员数量
     * 2. 使用轻量级成员查询（ChatMemberLite）
     * 3. 使用assembleLite方法组装数据
     * 
     * ## 参数说明
     * 
     * @param userId 用户ID，不能为null
     * @return AppInitVO 包含聊天室列表和用户状态列表，如果用户没有加入任何聊天室则返回空列表
     * 
     *         ## 数据流程
     *         1. 获取用户的所有聊天室 → 2. 获取轻量成员数据 → 3. 统计在线人数 →
     *         4. 获取未读消息数 → 5. 获取最后消息 → 6. 组装轻量数据
     */
    @Override
    public AppInitVO getChatVOListAndMemberStatusListLite(Integer userId) {
        // 1. 获取用户加入的所有聊天室列表
        List<ChatVO> chatVOList = chatMapper.getChatVOListByUserId(userId);
        if (chatVOList == null || chatVOList.isEmpty()) {
            // 返回空列表而不是null，保持接口一致性
            return new AppInitVO(Collections.emptyList(), Collections.emptyList());
        }

        // 2. 获取在线用户列表（WebSocket连接状态）
        Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();

        // 3. 获取轻量级成员信息（只包含必要字段）
        List<ChatMemberLite> liteMembers = chatMemberMapper.getChatMemberLiteListByUserId(userId);

        // 4. 统计每个聊天室的在线成员数量
        Map<String, Integer> onlineCountMap = new HashMap<>();
        Map<String, UserStatus> uniqueUserStatusMap = new HashMap<>();

        for (ChatMemberLite m : liteMembers) {
            boolean isOnline = chatAssembler.isUserOnline(m.getUserId(), onlineUsers);
            // 累加在线人数：如果在线则加1，否则加0
            onlineCountMap.merge(m.getChatCode(), isOnline ? 1 : 0, (a, b) -> a + b);
            uniqueUserStatusMap.putIfAbsent(
                    m.getUid(),
                    new UserStatus(m.getUid(), isOnline, m.getLastSeenAt()));
        }

        // 5. 获取未读消息数量映射
        Map<String, Integer> unreadCountMap = getUnreadCountMap(userId);

        // 6. 获取最后一条消息映射
        Map<String, MessageVO> lastMessageMap = messageMapper.getLastMessageListByUserId(userId);

        // 7. 为每个聊天室组装轻量数据
        for (ChatVO c : chatVOList) {
            chatAssembler.assembleLite(c,
                    lastMessageMap.get(c.getChatCode()),
                    unreadCountMap.getOrDefault(c.getChatCode(), 0),
                    onlineCountMap.getOrDefault(c.getChatCode(), 0));
        }

        // 8. 返回轻量版初始化数据
        return new AppInitVO(chatVOList, new ArrayList<>(uniqueUserStatusMap.values()));
    }

    // ============================================================
    // 3. 房间详情与权限逻辑 (Chat Detail & Security)
    // ============================================================

    /**
     * 获取聊天室详情（包含成员、最后消息、未读数等完整信息）
     * 
     * ## 功能描述
     * 为用户提供指定聊天室的完整详情信息，包括：
     * 1. 聊天室基本信息（名称、代码、头像等）
     * 2. 所有成员列表及其在线状态
     * 3. 最后一条消息内容
     * 4. 当前用户的未读消息数量
     * 
     * ## 使用场景
     * 1. 用户进入聊天室页面时加载完整信息
     * 2. 需要刷新聊天室详情时调用
     * 3. 显示右侧成员列表和聊天室信息
     * 
     * ## 权限验证
     * 1. 首先调用getChatId验证用户是否有权限访问该聊天室
     * 2. 如果用户不是成员，会抛出NOT_A_MEMBER异常
     * 3. 如果聊天室不存在，会抛出CHAT_NOT_FOUND异常
     * 
     * ## 参数说明
     * 
     * @param userId   用户ID，用于权限验证和获取未读消息数
     * @param chatCode 聊天室代码，8位数字
     * @return ChatVO 聊天室详情对象，如果聊天室不存在或用户无权限则返回null
     * 
     *         ## 数据流程
     *         1. 验证权限获取chatId → 2. 获取聊天室基本信息 → 3. 获取成员列表 →
     *         4. 获取最后消息 → 5. 获取未读数 → 6. 组装完整数据
     */
    @Override
    public ChatVO getChat(Integer userId, String chatCode) {
        // 1. 验证权限并获取聊天室ID（会检查用户是否为成员）
        Integer chatId = getChatId(userId, chatCode);

        // 2. 获取聊天室基本信息
        ChatVO chatVO = chatMapper.getChatVOByChatId(chatId);

        // 3. 如果聊天室存在，组装完整数据
        if (chatVO != null) {
            Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();
            List<ChatMember> members = chatMemberMapper.getChatMemberListByChatId(chatId);
            MessageVO lastMsg = messageMapper.getLastMessageByChatId(chatId);
            Integer unreadCount = messageMapper.getUnreadCountMapByUserIdAndChatId(userId, chatId);

            // 4. 使用ChatAssembler组装完整数据
            chatAssembler.assemble(chatVO, onlineUsers, lastMsg, unreadCount, members);
        }

        return chatVO;
    }

    /**
     * 获取聊天室成员列表（包含在线状态）
     * 
     * ## 功能描述
     * 为指定聊天室提供成员列表，每个成员包含：
     * 1. 成员基本信息（用户ID、昵称、头像等）
     * 2. 在线状态（通过WebSocket连接判断）
     * 3. 最后活跃时间
     * 
     * ## 使用场景
     * 1. 聊天室右侧成员列表的懒加载
     * 2. 需要单独刷新成员列表时
     * 3. 显示成员在线状态
     * 
     * ## 性能考虑
     * 1. 按需加载，避免在初始化时加载所有成员
     * 2. 使用ChatMemberVO轻量级对象，减少数据传输
     * 3. 在线状态实时从WebSocket服务器获取
     * 
     * ## 权限验证
     * 1. 首先调用getChatId验证用户是否有权限访问该聊天室
     * 2. 只有聊天室成员才能查看成员列表
     * 
     * ## 参数说明
     * 
     * @param userId   用户ID，用于权限验证
     * @param chatCode 聊天室代码，8位数字
     * @return List<ChatMemberVO> 成员列表，包含在线状态信息
     * 
     *         ## 数据流程
     *         1. 验证权限获取chatId → 2. 获取在线用户列表 → 3. 获取成员列表 → 4. 转换为VO对象
     */
    @Override
    public List<ChatMemberVO> getChatMemberVOList(Integer userId, String chatCode) {
        // 1. 验证权限并获取聊天室ID
        Integer chatId = getChatId(userId, chatCode);

        // 2. 获取当前在线用户列表（WebSocket连接）
        Map<Integer, Session> onlineUsers = WebSocketServer.getOnLineUserList();

        // 3. 获取聊天室成员列表
        List<ChatMember> members = chatMemberMapper.getChatMemberListByChatId(chatId);

        // 4. 转换为包含在线状态的VO对象
        return chatAssembler.toChatMemberVOList(members, onlineUsers);
    }

    /**
     * 获取聊天室ID并验证用户访问权限
     * 
     * ## 功能描述
     * 核心权限验证方法，用于：
     * 1. 根据chatCode查找对应的聊天室ID
     * 2. 验证用户是否有权限访问该聊天室（是否为成员）
     * 3. 记录非法访问尝试的日志
     * 
     * ## 使用场景
     * 1. 所有需要访问聊天室资源的方法的前置验证
     * 2. 确保只有聊天室成员才能访问聊天室数据
     * 3. 防止非法访问和越权操作
     * 
     * ## 安全考虑
     * 1. 双重验证：聊天室存在性 + 用户成员身份
     * 2. 详细的日志记录，便于安全审计
     * 3. 统一的异常处理，防止信息泄露
     * 
     * ## 异常情况
     * 1. 聊天室不存在：抛出CHAT_NOT_FOUND异常
     * 2. 用户不是成员：抛出NOT_A_MEMBER异常
     * 
     * ## 参数说明
     * 
     * @param userId   用户ID，用于权限验证
     * @param chatCode 聊天室代码，8位数字
     * @return Integer 聊天室ID，验证通过后返回
     * 
     *         ## 验证流程
     *         1. 根据chatCode查找chatId → 2. 检查聊天室是否存在 → 3. 检查用户是否为成员
     */
    @Override
    public Integer getChatId(Integer userId, String chatCode) {
        // 1. 根据聊天室代码查找聊天室ID
        Integer chatId = chatMapper.getChatIdByChatCode(chatCode);

        // 2. 验证聊天室是否存在
        if (chatId == null) {
            log.warn("[Illegal Request] User {} attempted to access non-existent chatCode: {}", userId, chatCode);
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND);
        }

        // 3. 验证用户是否为聊天室成员
        if (!chatMapper.isValidChatId(userId, chatId)) {
            log.warn("[Permission Denied] User {} attempted to access unjoined chat room: {}", userId, chatCode);
            throw new BusinessException(ErrorCode.NOT_A_MEMBER);
        }

        return chatId;
    }

    @Override
    public ChatJoinInfo getJoinInfo(Integer chatId) {
        return chatMapper.getJoinInfoByChatId(chatId);
    }

    /**
     * 验证聊天室加入请求的有效性
     * 
     * ## 功能描述
     * 在用户实际加入聊天室之前，验证加入请求的有效性，包括：
     * 1. 验证邀请码或房间代码的有效性
     * 2. 验证密码的正确性（如果是密码模式）
     * 3. 检查聊天室是否允许加入
     * 4. 返回安全的聊天室信息（去除敏感字段）
     * 
     * ## 使用场景
     * 1. 用户尝试加入聊天室前的预验证
     * 2. 前端需要显示聊天室基本信息但用户尚未加入
     * 3. 邀请链接的验证
     * 
     * ## 安全考虑
     * 1. 返回的ChatVO对象去除了敏感信息（如ownerUid、joinEnabled等）
     * 2. 密码使用哈希验证，不存储明文
     * 3. 邀请码使用SHA256哈希存储和验证
     * 4. 详细的错误信息，但不泄露系统内部细节
     * 
     * ## 验证流程
     * 1. 确定验证模式（邀请码或房间代码+密码）
     * 2. 查找对应的聊天室ID
     * 3. 获取聊天室加入配置信息
     * 4. 验证各种业务规则
     * 5. 返回安全的聊天室信息
     * 
     * ## 参数说明
     * 
     * @param req 验证请求，包含邀请码或房间代码+密码
     * @return ChatVO 安全的聊天室信息对象（去除敏感字段）
     * 
     *         ## 业务规则
     *         1. 邀请码模式：验证邀请码有效性、是否过期、是否被撤销
     *         2. 密码模式：验证密码是否正确、聊天室是否开启密码登录
     *         3. 通用规则：聊天室必须存在且允许加入
     */
    @Override
    public ChatVO validateChatJoin(ValidateChatJoinReq req) {
        Integer chatId = null;

        // 1. 确定验证模式并查找聊天室ID
        if (req.getInviteCode() != null && !req.getInviteCode().isBlank()) {
            // 获取邀请码对应的聊天室信息
            String hash = InviteCodeUtils.sha256Hex(req.getInviteCode());
            ChatInvite chatInvite = chatInviteMapper.findByCodeHash(hash);

            if (chatInvite == null) {
                throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
            }

            // 检查邀请码是否有效（未过期、未撤销、未超过使用次数）
            if (chatInvite.getExpiresAt() != null && chatInvite.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.INVITE_CODE_EXPIRED);
            }
            if (chatInvite.getRevoked() != null && chatInvite.getRevoked() == 1) {
                throw new BusinessException(ErrorCode.INVITE_CODE_REVOKED);
            }
            if (chatInvite.getMaxUses() > 0 && chatInvite.getUsedCount() >= chatInvite.getMaxUses()) {
                throw new BusinessException(ErrorCode.INVITE_CODE_USAGE_LIMIT_REACHED);
            }

            // 检查聊天室是否允许加入
            ChatJoinInfo chatInfo = getJoinInfo(chatInvite.getChatId());
            if (chatInfo == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Chat room does not exist");
            }
            if (chatInfo.getJoinEnabled() != null && chatInfo.getJoinEnabled() == 0) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Chat room has disabled joining");
            }

            chatId = chatInvite.getChatId();

        } else if (req.getChatCode() != null && !req.getChatCode().isBlank()) {
            // 房间代码+密码模式：密码必须提供
            if (req.getPassword() == null || req.getPassword().isEmpty()) {
                throw new BusinessException(ErrorCode.PASSWORD_REQUIRED,
                        "Password is required when chatCode is provided");
            }
            chatId = chatMapper.getChatIdByChatCode(req.getChatCode());
        } else {
            // 两种模式都未提供
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Either inviteCode or chatCode must be provided");
        }

        if (chatId == null) {
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND, "Chat room not found");
        }

        // 3. 获取聊天室加入配置信息
        ChatJoinInfo info = chatMapper.getJoinInfoByChatId(chatId);
        if (info == null || info.getChatId() == null) {
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND, "Chat room not found");
        }

        // 4. 验证聊天室是否允许加入
        if (info.getJoinEnabled() == null || info.getJoinEnabled() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Join is disabled for this chat room");
        }

        // 5. 如果是密码模式，验证密码
        if (req.getChatCode() != null && !req.getChatCode().isBlank()) {
            if (info.getChatPasswordHash() == null || info.getChatPasswordHash().isBlank()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Password login is not enabled for this chat room");
            }
            if (!PasswordUtils.matches(req.getPassword(), info.getChatPasswordHash())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Incorrect password");
            }
        }

        // 6. 获取聊天室基本信息
        ChatVO chatMapperChatVO = chatMapper.getChatVOByChatId(info.getChatId());
        if (chatMapperChatVO == null) {
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND, "Chat room not found");
        }

        // 7. 组装轻量级数据（不包含敏感信息）
        chatAssembler.assembleLite(chatMapperChatVO, null, null, null);

        // 8. 移除敏感字段，确保返回的信息安全
        chatMapperChatVO.setOwnerUid(null);
        chatMapperChatVO.setJoinEnabled(null);
        chatMapperChatVO.setLastActiveAt(null);
        chatMapperChatVO.setCreateTime(null);
        chatMapperChatVO.setUpdateTime(null);
        chatMapperChatVO.setUnreadCount(null);
        chatMapperChatVO.setLastMessage(null);
        chatMapperChatVO.setOnLineMemberCount(null);
        chatMapperChatVO.setChatMembers(null);

        // 9. 返回安全的聊天室信息
        return chatMapperChatVO;
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
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request body cannot be empty");
        }

        if (joinChatReq.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User ID cannot be empty");
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
            throw new BusinessException(ErrorCode.FORBIDDEN, "This chat room has disabled joining");
        }

        // 4. 执行加入
        LocalDateTime now = LocalDateTime.now();
        chatMemberMapper.add(chatInfo.getChatId(), joinChatReq.getUserId(), now);

        // 4.1. 插入系统消息 (Type 11: Member Join)
        // 区分正式用户与访客用户
        User user = userMapper.getUserById(joinChatReq.getUserId());
        String dbUsername = userMapper.getUsernameByUserId(joinChatReq.getUserId());
        boolean isGuest = (dbUsername == null);
        String displayName = (isGuest ? "[Guest] " : "") + user.getNickname();

        Message sysMsg = new Message();
        sysMsg.setChatId(chatInfo.getChatId());
        sysMsg.setSenderId(joinChatReq.getUserId());
        sysMsg.setType(11);
        sysMsg.setText(displayName);
        sysMsg.setAssetIds(null);
        sysMsg.setCreateTime(now);
        sysMsg.setUpdateTime(now);
        messageMapper.addMessage(sysMsg);

        // 4.2. WebSocket 广播：使用 JoinBroadcastVO 封装 System Message (Type 11) 和 Member
        // Info
        List<Integer> memberIds = chatMemberMapper.getChatMembersByUserIdAndChatId(joinChatReq.getUserId(),
                chatInfo.getChatId());

        // 构造 ChatMemberVO
        ChatMemberVO memberVO = getChatMemberVO(user, chatInfo, now);

        // 构造 JoinBroadcastVO
        JoinBroadcastVO broadcastVO = new JoinBroadcastVO();
        broadcastVO.setSender(user.getUid());
        broadcastVO.setChatCode(chatInfo.getChatCode());
        broadcastVO.setType(11);
        broadcastVO.setText(displayName);
        broadcastVO.setCreateTime(now);
        broadcastVO.setMember(memberVO);

        String jsonMsg = MessageUtils.setMessage(3001, "MEMBER_JOIN", broadcastVO);

        // [DEBUG] Log the payload to verify correct serialization
        log.info("[DEBUG] JoinBroadcastVO member: {}", broadcastVO.getMember());
        log.info("[DEBUG] Final JSON Message: {}", jsonMsg);

        WebSocketServer.broadcast(jsonMsg, memberIds);

        log.info("User joined chat room successfully: userId={}, chatId={}", joinChatReq.getUserId(),
                chatInfo.getChatId());

        // 5. 构建返回对象
        Chat chat = new Chat();
        chat.setId(chatInfo.getChatId());
        chat.setChatCode(chatInfo.getChatCode());
        chat.setJoinEnabled(chatInfo.getJoinEnabled());
        chat.setCreateTime(now);
        chat.setUpdateTime(now);
        return chat;
    }

    private static @NonNull ChatMemberVO getChatMemberVO(User user, ChatJoinInfo chatInfo, LocalDateTime now) {
        ChatMemberVO memberVO = new ChatMemberVO(
                user.getUid(),
                chatInfo.getChatCode(),
                user.getNickname(),
                null, // avatar (AssetService 暂未处理，后续优化)
                true, // online
                now);
        // 如果有 assetId，尝试转换 image (简化处理，暂留坑位)
        if (user.getAssetId() != null) {
            Image image = new Image();
            image.setAssetId(user.getAssetId());
            // 注意：这里没有 asset 的 URL 信息，若前端依赖 URL 则需要查库或调整 ChatMemberVO 构造 logic
            // 鉴于 ChatMemberVO 结构，通常需要完整的 Image 对象。
            // 临时方案：仅设置 assetId，前端若有预加载逻辑可复用；或者此处调用 assetService 获取完整 Image
            memberVO.setAvatar(image);
        }
        return memberVO;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CreateChatVO createChat(Integer userId, ChatReq chatReq) {
        if (userId == null)
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User is required");
        if (chatReq == null)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request body is required");
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
            if (chatReq.getAvatar().getAssetId() != null) {
                objectId = chatReq.getAvatar().getAssetId();
            } else if (chatReq.getAvatar().getImageName() != null) {
                Asset existingObject = assetService.findByObjectName(chatReq.getAvatar().getImageName());
                if (existingObject != null) {
                    objectId = existingObject.getId();
                } else {
                    log.warn("Chat avatar object not found in assets table: {}", chatReq.getAvatar().getImageName());
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
                if (i == 5)
                    throw new BusinessException(ErrorCode.DATABASE_ERROR, "Failed to generate unique chatCode");
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
                null);
        chatInviteMapper.insert(invite);

        // Insert System Message (Type 10: Room Created)
        Message sysMsg = new Message();
        sysMsg.setChatId(chatId);
        sysMsg.setSenderId(userId);
        sysMsg.setType(10); // System: room_created
        sysMsg.setText(null); // Frontend will localize this
        sysMsg.setAssetIds(null);
        sysMsg.setCreateTime(now);
        sysMsg.setUpdateTime(now);
        messageMapper.addMessage(sysMsg);

        return new CreateChatVO(chatCode, inviteCode);
    }

    // ============================================================
    // 5. 私有辅助方法 (Private Helper Methods)
    // ============================================================

    /**
     * 获取用户未读数 Map
     */
    private Map<String, Integer> getUnreadCountMap(Integer userId) {
        return messageMapper.getUnreadCountMapByUserId(userId).stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("chatCode"),
                        row -> ((Number) row.get("unreadCount")).intValue(),
                        (v1, v2) -> v1));
    }

    /**
     * 校验加入请求的基本参数合法性
     */
    private void validateJoinRequest(JoinChatReq req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request body cannot be empty");
        }
        boolean hasPwdArgs = req.getChatCode() != null && req.getPassword() != null;
        boolean hasInviteArgs = req.getInviteCode() != null;

        if (hasPwdArgs && hasInviteArgs) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Cannot use both password and invite code simultaneously");
        }
        if (!hasPwdArgs && !hasInviteArgs) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Missing parameters: either (chatCode + password) or (inviteCode) is required");
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
            throw new BusinessException(ErrorCode.FORBIDDEN, "Password login is not enabled for this room");
        }

        if (password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Password cannot be empty");
        }

        if (!PasswordUtils.matches(password, storedHash)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Incorrect password");
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
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid invite code");
        }
        if (Integer.valueOf(1).equals(invite.getRevoked())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invite code has been revoked");
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invite code has expired");
        }

        // 2. 剩余次数校验
        // 规则：如果 maxUses > 0 (即为 1)，且 usedCount 已经 >= maxUses，则耗尽
        if (invite.getMaxUses() > 0 && invite.getUsedCount() >= invite.getMaxUses()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invite code usage limit exceeded");
        }

        // 3. 预先获取房间信息 (防止邀请码有效但房间没了)
        ChatJoinInfo info = chatMapper.getJoinInfoByChatId(invite.getChatId());
        if (info == null) {
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND);
        }

        // 4. 消费邀请码 (CAS 原子更新，防止并发超用)
        // SQL 逻辑: UPDATE ... SET used_count = used_count + 1 WHERE hash = ? AND
        // (max_uses = 0 OR used_count < max_uses)
        int rows = chatInviteMapper.consume(invite.getChatId(), hash);
        if (rows <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invite code invalid or concurrent conflict");
        }

        // 5. 阅后即焚逻辑
        // 如果限制了次数（maxUses > 0，即为 1），且消费成功，则立即物理删除
        if (invite.getMaxUses() > 0) {
            chatInviteMapper.deleteByCodeHash(hash);
            log.info("Single-use invite code consumed and deleted: codeHash={}, chatId={}", hash, invite.getChatId());
        }

        return info;
    }

}