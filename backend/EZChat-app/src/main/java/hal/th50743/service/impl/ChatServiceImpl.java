package hal.th50743.service.impl;

// 项目自定义组件
import hal.th50743.assembler.ChatAssembler; // 聊天数据组装器
import hal.th50743.assembler.ChatMemberAssembler; // 聊天成员组装器
import hal.th50743.exception.BusinessException; // 业务异常类
import hal.th50743.exception.ErrorCode; // 错误代码枚举
import hal.th50743.mapper.ChatInviteMapper; // 聊天邀请数据访问层
import hal.th50743.mapper.ChatMapper; // 聊天室数据访问层
import hal.th50743.mapper.ChatMemberMapper; // 聊天成员数据访问层
import hal.th50743.mapper.MessageMapper; // 消息数据访问层
import hal.th50743.mapper.UserMapper; // 用户数据访问层
import hal.th50743.pojo.*; // 所有数据对象（VO、DTO、Entity）

// 工具类
import hal.th50743.utils.*; // 项目工具类（密码、邀请码、消息、图片等）

// 服务层接口
import hal.th50743.service.ChatService; // 聊天服务接口
import hal.th50743.service.AssetService; // 资源文件服务接口

// WebSocket 相关
import hal.th50743.ws.WebSocketServer; // WebSocket 服务端点
import jakarta.websocket.Session; // WebSocket 会话对象

// Minio 对象存储
// import io.minio.MinioOSSOperator;  (Removed)

// Lombok 注解
import lombok.RequiredArgsConstructor; // 构造器注入注解
import lombok.extern.slf4j.Slf4j; // SLF4J 日志注解

// JSpecify 注解
// import org.jspecify.annotations.NonNull; (Removed)

// Spring 框架
import org.springframework.dao.DuplicateKeyException; // 数据库唯一键冲突异常
import org.springframework.stereotype.Service; // 服务层组件注解
import org.springframework.transaction.annotation.Transactional; // 事务管理注解

// Java 标准库
import java.time.LocalDateTime; // 本地日期时间
import java.util.*; // 集合框架
import java.util.stream.Collectors; // Stream API 收集器

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

    // ============================================================
    // 依赖注入组件（由 Spring 容器管理）
    // ============================================================

    /** 聊天室数据访问层 - 负责聊天室基础信息的 CRUD 操作 */
    private final ChatMapper chatMapper;

    /** 聊天成员数据访问层 - 负责聊天室成员关系的管理 */
    private final ChatMemberMapper chatMemberMapper;

    /** 聊天邀请数据访问层 - 负责邀请码的生成、验证和消费 */
    private final ChatInviteMapper chatInviteMapper;

    /** 消息数据访问层 - 负责消息的存储和查询 */
    private final MessageMapper messageMapper;

    /** 用户数据访问层 - 负责用户信息的查询 */
    private final UserMapper userMapper;

    /** 资源文件服务 - 负责图片等资源的存储和管理 */
    private final AssetService assetService;

    /** 聊天数据组装器 - 负责将原始数据组装为前端需要的 VO 对象 */
    private final ChatAssembler chatAssembler;

    /** 聊天成员组装器 - 负责成员数据的组装和转换 */
    private final ChatMemberAssembler chatMemberAssembler;

    /** Minio 对象存储操作器 - 负责文件的上传、下载和 URL 生成 */
    // private final MinioOSSOperator minioOSSOperator; (Removed)

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
            boolean isOnline = chatMemberAssembler.isUserOnline(m.getUserId(), onlineUsers);
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
        return chatMemberAssembler.toChatMemberVOList(members, onlineUsers);
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

    /**
     * 获取聊天室加入配置信息
     * 
     * <p>
     * 查询指定聊天室的加入相关配置，包括：
     * </p>
     * <ul>
     * <li>是否允许加入（joinEnabled）</li>
     * <li>密码哈希（chatPasswordHash）</li>
     * <li>聊天室代码（chatCode）</li>
     * <li>聊天室ID（chatId）</li>
     * </ul>
     * 
     * <p>
     * <b>使用场景</b>：
     * </p>
     * <ol>
     * <li>用户加入聊天室前的权限验证</li>
     * <li>密码模式加入时的密码验证</li>
     * <li>检查聊天室是否允许新成员加入</li>
     * </ol>
     * 
     * <p>
     * <b>性能考虑</b>：直接调用 Mapper 层，不进行额外的业务逻辑处理。
     * </p>
     * 
     * @param chatId 聊天室ID，不能为null
     * @return ChatJoinInfo 聊天室加入配置信息，如果聊天室不存在则返回null
     * 
     * @see ChatJoinInfo 加入配置信息数据对象
     */
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
        chatAssembler.sanitizeForPublic(chatMapperChatVO);

        // 9. 返回安全的聊天室信息
        return chatMapperChatVO;
    }

    // ============================================================
    // 4. 其他逻辑
    // ============================================================

    /**
     * 获取用户的所有聊天成员ID列表
     * 
     * <p>
     * 查询指定用户在所有聊天室中的联系人ID列表。主要用于：
     * </p>
     * <ul>
     * <li>WebSocket 上线状态广播</li>
     * <li>用户状态同步给所有联系人</li>
     * <li>消息推送的目标用户筛选</li>
     * </ul>
     * 
     * <p>
     * <b>业务逻辑</b>：返回用户在所有聊天室中的所有其他成员的用户ID，
     * 不包括用户自己，且去重处理。
     * </p>
     * 
     * <p>
     * <b>性能考虑</b>：
     * </p>
     * <ul>
     * <li>直接调用 Mapper 层，由数据库进行去重和筛选</li>
     * <li>返回的是用户ID列表，数据量较小</li>
     * <li>适用于频繁调用的场景（如用户状态变更）</li>
     * </ul>
     * 
     * @param userId 用户ID，不能为null
     * @return List<Integer> 用户的所有聊天成员ID列表，如果没有聊天成员则返回空列表
     * 
     * @see WebSocketServer#broadcast(String, List) WebSocket 广播使用此列表
     */
    @Override
    public List<Integer> getChatMembers(Integer userId) {
        return chatMemberMapper.getChatMembersById(userId);
    }

    /**
     * 用户加入聊天室 - 核心业务方法
     * 
     * <p>
     * 处理用户加入聊天室的完整流程，支持两种加入模式：
     * </p>
     * <ol>
     * <li><b>邀请码模式</b>：使用一次性或多次使用的邀请码加入</li>
     * <li><b>密码模式</b>：使用房间代码和密码加入</li>
     * </ol>
     * 
     * <h3>事务管理</h3>
     * <ul>
     * <li><b>传播行为</b>：REQUIRED（默认）</li>
     * <li><b>回滚策略</b>：遇到任何异常都回滚（rollbackFor = Exception.class）</li>
     * <li><b>业务一致性</b>：确保成员关系、系统消息、邀请码消费的原子性</li>
     * </ul>
     * 
     * <h3>业务流程</h3>
     * <ol>
     * <li>参数验证和请求合法性检查</li>
     * <li>分策略处理（邀请码/密码）并获取聊天室信息</li>
     * <li>全局权限检查（聊天室是否允许加入）</li>
     * <li>执行加入操作（添加成员关系）</li>
     * <li>插入系统消息（Type 11: 成员加入）</li>
     * <li>WebSocket 广播通知所有成员</li>
     * <li>构建并返回加入结果</li>
     * </ol>
     * 
     * <h3>系统消息设计</h3>
     * <ul>
     * <li><b>消息类型</b>：11（成员加入）</li>
     * <li><b>内容格式</b>：区分正式用户和访客用户</li>
     * <li><b>国际化</b>：前端根据消息类型进行本地化显示</li>
     * </ul>
     * 
     * <h3>WebSocket 广播</h3>
     * <ul>
     * <li><b>消息类型</b>：3001（MEMBER_JOIN）</li>
     * <li><b>数据封装</b>：使用 JoinBroadcastVO 包含系统消息和成员信息</li>
     * <li><b>目标用户</b>：聊天室所有现有成员（包括新加入者）</li>
     * </ul>
     * 
     * @param joinChatReq 加入请求对象，包含用户ID和加入凭证
     * @return Chat 聊天室基本信息对象
     * @throws BusinessException 各种业务异常，包括参数错误、权限不足、资源不存在等
     * 
     * @see JoinChatReq 加入请求数据对象
     * @see JoinBroadcastVO 加入广播数据对象
     * @see MessageUtils#setMessage(int, String, Object) 消息封装工具
     * @see WebSocketServer#broadcast(String, List) WebSocket 广播方法
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Chat join(JoinChatReq joinChatReq) {
        // ========== 步骤1: 基础参数验证 ==========
        if (joinChatReq == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request body cannot be empty");
        }

        if (joinChatReq.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User ID cannot be empty");
        }

        // 1.1 参数校验：验证请求模式的合法性（不能同时使用两种模式）
        validateJoinRequest(joinChatReq);

        ChatJoinInfo chatInfo;

        // ========== 步骤2: 分策略处理加入模式 ==========
        // 2.1 邀请码模式优先处理
        if (joinChatReq.getInviteCode() != null) {
            chatInfo = handleInviteJoin(joinChatReq.getInviteCode());
        } else {
            // 2.2 密码模式处理
            chatInfo = handlePasswordJoin(joinChatReq.getChatCode(), joinChatReq.getPassword());
        }

        // ========== 步骤3: 全局权限检查 ==========
        // 检查聊天室是否允许新成员加入（joinEnabled == 1）
        if (Integer.valueOf(0).equals(chatInfo.getJoinEnabled())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This chat room has disabled joining");
        }

        // ========== 步骤4: 执行加入操作 ==========
        LocalDateTime now = LocalDateTime.now();
        // 4.1 添加成员关系到数据库
        chatMemberMapper.add(chatInfo.getChatId(), joinChatReq.getUserId(), now);

        // ========== 步骤4.1: 插入系统消息 ==========
        // 查询用户信息，用于区分正式用户和访客用户
        User user = userMapper.getUserById(joinChatReq.getUserId());
        Message sysMsg = null;
        String displayName = "Unknown User"; // 默认显示名称

        if (user == null) {
            // 用户不存在异常：理论上不应该发生，因为用户ID来自已验证的Token
            log.error("Failed to find user for system message: info={}", joinChatReq);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found after join");
        } else {
            // UserMapper.getUserById 已经关联了 formal_users 表
            // 如果 username 为 null，说明是访客用户
            String dbUsername = user.getUsername();
            boolean isGuest = (dbUsername == null);
            // 构建显示名称：访客用户添加 [Guest] 前缀
            displayName = (isGuest ? "[Guest] " : "")
                    + (user.getNickname() != null ? user.getNickname() : "Unknown");

            // 创建系统消息对象
            sysMsg = new Message();
            sysMsg.setChatId(chatInfo.getChatId()); // 聊天室ID
            sysMsg.setSenderId(joinChatReq.getUserId()); // 发送者ID（新加入的用户）
            sysMsg.setType(11); // 消息类型：11 表示成员加入
            sysMsg.setText(displayName); // 显示名称（前端会进行本地化）
            sysMsg.setAssetIds(""); // 确保不为null，数据库安全性
            sysMsg.setCreateTime(now); // 创建时间
            sysMsg.setUpdateTime(now); // 更新时间
            messageMapper.addMessage(sysMsg); // 持久化到数据库
            log.info("System message persisted for join: msgId={}, text={}", sysMsg.getId(), displayName);
        }

        // ========== 步骤4.2: WebSocket 广播通知 ==========
        // 4.2.1 获取聊天室所有成员ID列表（包括新加入的用户）
        // 重要：显式查询确保不会漏掉任何人，特别是当发送者是访客时
        List<Integer> memberIds = chatMemberMapper.getChatMemberListByChatId(chatInfo.getChatId())
                .stream()
                .map(ChatMember::getUserId)
                .collect(Collectors.toList());

        // 4.2.2 构造广播数据对象（包含成员信息）
        JoinBroadcastVO broadcastVO = chatMemberAssembler.toJoinBroadcastVO(user, chatInfo.getChatCode(), displayName,
                now);

        // 4.2.3 封装为 WebSocket 消息并广播
        // 消息格式：状态码 3001 表示成员加入事件
        String jsonMsg = MessageUtils.setMessage(3001, "MEMBER_JOIN", broadcastVO);
        WebSocketServer.broadcast(jsonMsg, memberIds);

        // ========== 步骤5: 记录成功日志 ==========
        log.info("User joined chat room successfully: userId={}, chatId={}", joinChatReq.getUserId(),
                chatInfo.getChatId());

        // ========== 步骤6: 构建返回对象 ==========
        Chat chat = new Chat();
        chat.setId(chatInfo.getChatId()); // 聊天室ID
        chat.setChatCode(chatInfo.getChatCode()); // 聊天室代码
        chat.setJoinEnabled(chatInfo.getJoinEnabled()); // 是否允许加入
        chat.setCreateTime(now); // 创建时间（当前时间）
        chat.setUpdateTime(now); // 更新时间（当前时间）
        return chat;
    }

    /**
     * 构建聊天成员视图对象（包含头像信息）
     * 
     * <p>
     * 根据用户信息和聊天室信息，构建完整的 ChatMemberVO 对象，
     * 特别处理用户头像的加载和 URL 生成。
     * </p>
     * 
     * <h3>头像处理流程</h3>
     * <ol>
     * <li>检查用户是否有头像资源ID（assetId）</li>
     * <li>通过 AssetService 查询资源文件信息</li>
     * <li>使用 MinioOSSOperator 生成图片的访问URL</li>
     * <li>构建完整的 Image 对象并设置到成员信息中</li>
     * <li>提供降级方案：当资源不存在时创建基础 Image 对象</li>
     * </ol>
     * 
     * <h3>性能考虑</h3>
     * <ul>
     * <li>头像信息按需加载，避免不必要的资源查询</li>
     * <li>使用缓存机制（AssetService 内部可能实现）</li>
     * <li>Minio URL 生成是轻量级操作</li>
     * </ul>
     * 
     * @param user     用户信息对象，包含用户基本信息和头像资源ID
     * @param chatInfo 聊天室加入信息，包含聊天室代码等
     * @param now      当前时间，用于设置成员的最后活跃时间
     * @return ChatMemberVO 完整的聊天成员视图对象
     * 
     * @see AssetService#findById(Integer) 资源文件查询服务
     * @see ImageUtils#buildImage(String, MinioOSSOperator) 图片URL生成工具
     * @see MinioOSSOperator Minio对象存储操作器
     */
    // 方法已移动到 ChatMemberAssembler.toChatMemberVO
    // private @NonNull ChatMemberVO getChatMemberVO(User user, ChatJoinInfo
    // chatInfo, LocalDateTime now) { ... }

    /**
     * 创建新聊天室 - 核心业务方法
     * 
     * <p>
     * 处理用户创建新聊天室的完整流程，包括：
     * </p>
     * <ol>
     * <li>参数验证和业务规则检查</li>
     * <li>生成唯一的聊天室代码（8位数字）</li>
     * <li>设置聊天室基本信息和配置</li>
     * <li>创建者自动加入聊天室</li>
     * <li>生成邀请码并设置有效期</li>
     * <li>插入系统消息（Type 10: 房间创建）</li>
     * </ol>
     * 
     * <h3>事务管理</h3>
     * <ul>
     * <li><b>传播行为</b>：REQUIRED（默认）</li>
     * <li><b>回滚策略</b>：遇到任何异常都回滚（rollbackFor = Exception.class）</li>
     * <li><b>业务一致性</b>：确保聊天室、成员关系、邀请码、系统消息的原子性</li>
     * </ul>
     * 
     * <h3>聊天室代码生成</h3>
     * <ul>
     * <li><b>长度</b>：8位数字</li>
     * <li><b>唯一性</b>：使用重试机制确保唯一（最多重试5次）</li>
     * <li><b>生成算法</b>：UidGenerator.generateUid(8)</li>
     * <li><b>冲突处理</b>：捕获 DuplicateKeyException 并重试</li>
     * </ul>
     * 
     * <h3>邀请码配置</h3>
     * <ul>
     * <li><b>长度</b>：18位字符（字母数字）</li>
     * <li><b>存储</b>：存储 SHA256 哈希值，不存储明文</li>
     * <li><b>有效期</b>：默认7天（10080分钟），可自定义</li>
     * <li><b>使用次数</b>：0表示无限次，1表示一次性使用</li>
     * </ul>
     * 
     * <h3>系统消息设计</h3>
     * <ul>
     * <li><b>消息类型</b>：10（房间创建）</li>
     * <li><b>内容</b>：null（前端根据消息类型进行本地化）</li>
     * <li><b>发送者</b>：创建者用户ID</li>
     * </ul>
     * 
     * @param userId  创建者用户ID，不能为null
     * @param chatReq 创建请求对象，包含聊天室配置信息
     * @return CreateChatVO 创建结果对象，包含聊天室代码和邀请码
     * @throws BusinessException 各种业务异常，包括参数错误、权限不足、系统错误等
     * 
     * @see ChatReq 创建请求数据对象
     * @see CreateChatVO 创建结果数据对象
     * @see UidGenerator#generateUid(int) 唯一ID生成器
     * @see InviteCodeUtils#generateInviteCode(int) 邀请码生成工具
     * @see InviteCodeUtils#sha256Hex(String) SHA256哈希工具
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public CreateChatVO createChat(Integer userId, ChatReq chatReq) {
        // ========== 步骤1: 基础参数验证 ==========
        if (userId == null)
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User is required");
        if (chatReq == null)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request body is required");
        if (chatReq.getChatName() == null || chatReq.getChatName().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "chatName is required");
        }

        // ========== 步骤2: 处理密码配置 ==========
        int joinEnabled = chatReq.getJoinEnable() == null ? 1 : chatReq.getJoinEnable();
        String passwordHash = null;
        String password = chatReq.getPassword();
        String confirm = chatReq.getPasswordConfirm();

        // 2.1 如果提供了密码，需要验证和加密
        if (password != null && !password.isBlank()) {
            if (!password.equals(confirm)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Password confirm mismatch");
            }
            passwordHash = PasswordUtils.encode(password); // BCrypt 加密
        }

        // ========== 步骤3: 处理头像配置 ==========
        Integer objectId = null;
        if (chatReq.getAvatar() != null) {
            // 3.1 优先使用 assetId（如果前端已经上传了头像）
            if (chatReq.getAvatar().getAssetId() != null) {
                objectId = chatReq.getAvatar().getAssetId();
            }
            // 3.2 其次使用 imageName（兼容旧版本或直接指定文件名）
            else if (chatReq.getAvatar().getImageName() != null) {
                Asset existingObject = assetService.findByObjectName(chatReq.getAvatar().getImageName());
                if (existingObject != null) {
                    objectId = existingObject.getId();
                } else {
                    // 头像文件不存在，记录警告但不阻止创建
                    log.warn("Chat avatar object not found in assets table: {}", chatReq.getAvatar().getImageName());
                }
            }
        }

        // ========== 步骤4: 生成唯一聊天室代码 ==========
        String chatCode = null;
        ChatCreate chatCreate = new ChatCreate();
        chatCreate.setChatName(chatReq.getChatName()); // 聊天室名称
        chatCreate.setOwnerId(userId); // 创建者ID
        chatCreate.setJoinEnabled(joinEnabled); // 是否允许加入
        chatCreate.setChatPasswordHash(passwordHash); // 密码哈希（可能为null）
        chatCreate.setObjectId(objectId); // 头像资源ID（可能为null）

        // 4.1 重试机制：最多尝试5次生成唯一聊天室代码
        for (int i = 1; i <= 5; i++) {
            chatCode = UidGenerator.generateUid(8); // 生成8位数字代码
            chatCreate.setChatCode(chatCode);
            try {
                chatMapper.insertChat(chatCreate); // 插入数据库
                break; // 成功则退出循环
            } catch (DuplicateKeyException e) {
                // 唯一键冲突：聊天室代码已存在，需要重试
                if (i == 5) {
                    // 第5次仍然冲突，抛出系统错误
                    throw new BusinessException(ErrorCode.DATABASE_ERROR, "Failed to generate unique chatCode");
                }
                // 继续下一次尝试
            }
        }

        // ========== 步骤5: 验证聊天室创建结果 ==========
        Integer chatId = chatCreate.getId();
        if (chatId == null) {
            // 理论上不应该发生，但为了健壮性需要检查
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Create chat failed (no chatId)");
        }

        // ========== 步骤6: 创建者自动加入聊天室 ==========
        LocalDateTime now = LocalDateTime.now();
        chatMemberMapper.add(chatId, userId, now); // 添加创建者为第一个成员

        // ========== 步骤7: 生成邀请码 ==========
        // 7.1 计算邀请码有效期（默认7天：10080分钟）
        int expiryMinutes = chatReq.getJoinLinkExpiryMinutes() == null ? 10080 : chatReq.getJoinLinkExpiryMinutes();
        LocalDateTime expiresAt = now.plusMinutes(Math.max(1, expiryMinutes)); // 至少1分钟

        // 7.2 设置使用次数限制（0=无限次，1=一次性）
        int maxUses = chatReq.getMaxUses() == null ? 0 : chatReq.getMaxUses();
        if (maxUses != 0 && maxUses != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "maxUses must be 0 or 1");
        }

        // 7.3 生成邀请码并计算哈希值
        String inviteCode = InviteCodeUtils.generateInviteCode(18); // 18位邀请码
        String codeHash = InviteCodeUtils.sha256Hex(inviteCode); // SHA256 哈希

        // 7.4 创建邀请码记录
        ChatInvite invite = new ChatInvite(
                null, // ID（自增）
                chatId, // 聊天室ID
                codeHash, // 邀请码哈希值
                expiresAt, // 过期时间
                maxUses, // 最大使用次数
                0, // 已使用次数（初始为0）
                0, // 是否撤销（0=未撤销）
                userId, // 创建者用户ID
                null, // 创建时间（数据库默认值）
                null); // 更新时间（数据库默认值）

        chatInviteMapper.insert(invite); // 插入数据库

        // ========== 步骤8: 插入系统消息 ==========
        // 8.1 创建房间创建系统消息（Type 10）
        Message sysMsg = new Message();
        sysMsg.setChatId(chatId); // 聊天室ID
        sysMsg.setSenderId(userId); // 发送者ID（创建者）
        sysMsg.setType(10); // 消息类型：10 表示房间创建
        sysMsg.setText(null); // 文本内容为null，前端进行本地化
        sysMsg.setAssetIds(null); // 资源文件ID（无）
        sysMsg.setCreateTime(now); // 创建时间
        sysMsg.setUpdateTime(now); // 更新时间
        messageMapper.addMessage(sysMsg); // 插入数据库

        // ========== 步骤9: 返回创建结果 ==========
        return new CreateChatVO(chatCode, inviteCode);
    }

    // ============================================================
    // 5. 私有辅助方法 (Private Helper Methods)
    // ============================================================

    /**
     * 获取用户未读数映射表
     * 
     * <p>
     * 查询指定用户在所有聊天室中的未读消息数量，返回以聊天室代码为Key的映射表。
     * </p>
     * 
     * <h3>业务逻辑</h3>
     * <ul>
     * <li>查询用户在所有已加入聊天室中的未读消息数量</li>
     * <li>返回 Map<聊天室代码, 未读数量> 结构</li>
     * <li>使用 Stream API 进行数据转换和收集</li>
     * </ul>
     * 
     * <h3>性能考虑</h3>
     * <ul>
     * <li>单次数据库查询获取所有聊天室的未读数</li>
     * <li>内存中转换为映射表，减少后续查询次数</li>
     * <li>适用于批量处理场景（如应用初始化）</li>
     * </ul>
     * 
     * <h3>数据格式</h3>
     * 
     * <pre>
     * {
     *   "12345678": 5,    // 聊天室代码 12345678 有 5 条未读消息
     *   "87654321": 0,    // 聊天室代码 87654321 没有未读消息
     *   ...
     * }
     * </pre>
     * 
     * @param userId 用户ID，不能为null
     * @return Map<String, Integer> 未读数映射表，Key为聊天室代码，Value为未读数量
     * 
     * @see MessageMapper#getUnreadCountMapByUserId(Integer) 未读数查询方法
     */
    private Map<String, Integer> getUnreadCountMap(Integer userId) {
        return messageMapper.getUnreadCountMapByUserId(userId).stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("chatCode"), // Key: 聊天室代码
                        row -> ((Number) row.get("unreadCount")).intValue(), // Value: 未读数量
                        (v1, v2) -> v1)); // 合并函数（取第一个值）
    }

    /**
     * 校验加入请求的基本参数合法性
     * 
     * <p>
     * 验证加入聊天室请求的参数组合是否合法，确保请求符合业务规则：
     * </p>
     * <ol>
     * <li>请求体不能为null</li>
     * <li>不能同时使用密码模式和邀请码模式</li>
     * <li>必须提供一种有效的参数组合</li>
     * </ol>
     * 
     * <h3>参数组合规则</h3>
     * <table>
     * <tr>
     * <th>模式</th>
     * <th>必需参数</th>
     * <th>说明</th>
     * </tr>
     * <tr>
     * <td>密码模式</td>
     * <td>chatCode + password</td>
     * <td>两者必须同时提供</td>
     * </tr>
     * <tr>
     * <td>邀请码模式</td>
     * <td>inviteCode</td>
     * <td>只需邀请码</td>
     * </tr>
     * </table>
     * 
     * <h3>验证逻辑</h3>
     * <ul>
     * <li>密码模式：chatCode 和 password 必须同时存在</li>
     * <li>邀请码模式：inviteCode 必须存在</li>
     * <li>互斥规则：两种模式不能同时使用</li>
     * </ul>
     * 
     * <h3>异常情况</h3>
     * <ul>
     * <li>请求体为null：BAD_REQUEST</li>
     * <li>同时使用两种模式：BAD_REQUEST</li>
     * <li>未提供任何有效参数：BAD_REQUEST</li>
     * </ul>
     * 
     * @param req 加入请求对象
     * @throws BusinessException 参数验证失败时抛出业务异常
     * 
     * @see JoinChatReq 加入请求数据对象
     * @see ErrorCode#BAD_REQUEST 错误代码
     */
    private void validateJoinRequest(JoinChatReq req) {
        // 1. 基础验证：请求体不能为null
        if (req == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request body cannot be empty");
        }

        // 2. 检查参数组合
        boolean hasPwdArgs = req.getChatCode() != null && req.getPassword() != null;
        boolean hasInviteArgs = req.getInviteCode() != null;

        // 3. 互斥验证：不能同时使用两种模式
        if (hasPwdArgs && hasInviteArgs) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Cannot use both password and invite code simultaneously");
        }

        // 4. 完整性验证：必须提供一种有效的参数组合
        if (!hasPwdArgs && !hasInviteArgs) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Missing parameters: either (chatCode + password) or (inviteCode) is required");
        }
    }

    /**
     * 处理密码模式加入
     * 
     * <p>
     * 验证密码模式加入请求的合法性，包括：
     * </p>
     * <ol>
     * <li>聊天室代码的有效性</li>
     * <li>聊天室是否存在且允许加入</li>
     * <li>密码是否正确（BCrypt 哈希验证）</li>
     * <li>聊天室是否启用了密码登录功能</li>
     * </ol>
     * 
     * <h3>业务规则</h3>
     * <ul>
     * <li>聊天室必须存在且未禁用加入功能</li>
     * <li>聊天室必须设置了密码哈希（chatPasswordHash 不为空）</li>
     * <li>提供的密码必须与存储的哈希值匹配</li>
     * <li>密码不能为空</li>
     * </ul>
     * 
     * <h3>安全考虑</h3>
     * <ul>
     * <li>使用 BCrypt 哈希验证，避免密码明文存储和传输</li>
     * <li>详细的错误信息但不泄露系统细节</li>
     * <li>记录非法访问尝试的日志</li>
     * </ul>
     * 
     * <h3>性能优化建议</h3>
     * <ul>
     * <li>当前实现需要两次数据库查询（chatId + joinInfo）</li>
     * <li>可优化为单次查询：Mapper 增加 getJoinInfoByChatCode 方法</li>
     * </ul>
     * 
     * @param chatCode 聊天室代码，8位数字，不能为null或空
     * @param password 用户提供的密码，不能为null或空
     * @return ChatJoinInfo 聊天室加入配置信息（验证通过后）
     * @throws BusinessException 各种验证失败异常
     * 
     * @see PasswordUtils#matches(String, String) 密码验证工具
     * @see ChatMapper#getChatIdByChatCode(String) 聊天室代码查询
     * @see ChatMapper#getJoinInfoByChatId(Integer) 加入信息查询
     */
    private ChatJoinInfo handlePasswordJoin(String chatCode, String password) {
        // ========== 步骤1: 验证聊天室代码有效性 ==========
        // 1.1 根据聊天室代码查找聊天室ID
        Integer chatId = chatMapper.getChatIdByChatCode(chatCode);
        if (chatId == null) {
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND, "Chat room not found");
        }

        // 1.2 获取聊天室加入配置信息
        ChatJoinInfo info = chatMapper.getJoinInfoByChatId(chatId);
        if (info == null || info.getChatId() == null) {
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND);
        }

        // ========== 步骤2: 验证密码登录功能是否启用 ==========
        // 2.1 检查聊天室是否设置了密码哈希
        String storedHash = info.getChatPasswordHash();
        if (storedHash == null || storedHash.isBlank()) {
            // 业务规则：如果房间未设置密码哈希，则禁止通过密码模式加入（仅允许邀请）
            throw new BusinessException(ErrorCode.FORBIDDEN, "Password login is not enabled for this room");
        }

        // ========== 步骤3: 验证密码参数 ==========
        // 3.1 密码不能为空
        if (password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Password cannot be empty");
        }

        // ========== 步骤4: 验证密码正确性 ==========
        // 4.1 使用 BCrypt 验证密码哈希
        if (!PasswordUtils.matches(password, storedHash)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Incorrect password");
        }

        // ========== 步骤5: 返回验证通过的聊天室信息 ==========
        return info;
    }

    /**
     * 处理邀请码模式加入
     * 
     * <p>
     * 验证邀请码模式加入请求的合法性，并执行邀请码的消费逻辑。
     * </p>
     * 
     * <h3>业务规则</h3>
     * <ol>
     * <li><b>maxUses == 0</b>：无限使用，仅累加使用计数</li>
     * <li><b>maxUses == 1</b>：一次性使用，使用成功后立即物理删除（阅后即焚）</li>
     * </ol>
     * 
     * <h3>验证流程</h3>
     * <ol>
     * <li>基础校验：邀请码是否存在、是否被撤销、是否过期</li>
     * <li>剩余次数校验：检查使用次数限制</li>
     * <li>房间存在性验证：防止邀请码有效但房间不存在</li>
     * <li>原子消费：CAS 更新防止并发超用</li>
     * <li>阅后即焚：一次性邀请码使用后立即删除</li>
     * </ol>
     * 
     * <h3>安全考虑</h3>
     * <ul>
     * <li>存储邀请码的 SHA256 哈希值，不存储明文</li>
     * <li>使用 CAS（Compare-And-Swap）原子更新，防止并发超用</li>
     * <li>一次性邀请码使用后立即物理删除，防止重复使用</li>
     * <li>验证房间存在性，防止邀请码有效但房间已删除</li>
     * </ul>
     * 
     * <h3>并发控制</h3>
     * <ul>
     * <li>SQL 使用条件更新：WHERE hash = ? AND (max_uses = 0 OR used_count <
     * max_uses)</li>
     * <li>返回受影响的行数，判断更新是否成功</li>
     * <li>防止多个请求同时消费同一个邀请码导致超用</li>
     * </ul>
     * 
     * @param inviteCode 邀请码明文，18位字符，不能为null或空
     * @return ChatJoinInfo 聊天室加入配置信息（验证通过后）
     * @throws BusinessException 各种验证失败异常
     * 
     * @see InviteCodeUtils#sha256Hex(String) 邀请码哈希计算
     * @see ChatInviteMapper#findByCodeHash(String) 邀请码查询
     * @see ChatInviteMapper#consume(Integer, String) 邀请码消费
     * @see ChatInviteMapper#deleteByCodeHash(String) 邀请码删除
     */
    private ChatJoinInfo handleInviteJoin(String inviteCode) {
        // ========== 步骤1: 计算邀请码哈希并查询 ==========
        // 1.1 计算 SHA256 哈希值（安全存储，不存储明文）
        String hash = InviteCodeUtils.sha256Hex(inviteCode);
        // 1.2 根据哈希值查询邀请码记录
        ChatInvite invite = chatInviteMapper.findByCodeHash(hash);

        // ========== 步骤2: 基础校验 ==========
        // 2.1 邀请码是否存在
        if (invite == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid invite code");
        }
        // 2.2 邀请码是否被撤销（revoked == 1）
        if (Integer.valueOf(1).equals(invite.getRevoked())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invite code has been revoked");
        }
        // 2.3 邀请码是否过期
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invite code has expired");
        }

        // ========== 步骤3: 剩余次数校验 ==========
        // 规则：如果 maxUses > 0 (即为 1)，且 usedCount 已经 >= maxUses，则耗尽
        if (invite.getMaxUses() > 0 && invite.getUsedCount() >= invite.getMaxUses()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invite code usage limit exceeded");
        }

        // ========== 步骤4: 预先获取房间信息 ==========
        // 重要：防止邀请码有效但房间已经被删除的情况
        ChatJoinInfo info = chatMapper.getJoinInfoByChatId(invite.getChatId());
        if (info == null) {
            throw new BusinessException(ErrorCode.CHAT_NOT_FOUND);
        }

        // ========== 步骤5: 原子消费邀请码 ==========
        // 5.1 CAS 更新：原子增加 used_count，同时检查使用限制
        // SQL 逻辑: UPDATE ... SET used_count = used_count + 1
        // WHERE hash = ? AND (max_uses = 0 OR used_count < max_uses)
        int rows = chatInviteMapper.consume(invite.getChatId(), hash);

        // 5.2 检查更新结果
        if (rows <= 0) {
            // 更新失败：可能原因包括并发冲突、邀请码已被删除等
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invite code invalid or concurrent conflict");
        }

        // ========== 步骤6: 阅后即焚逻辑 ==========
        // 6.1 如果限制了次数（maxUses > 0，即为 1），且消费成功，则立即物理删除
        if (invite.getMaxUses() > 0) {
            chatInviteMapper.deleteByCodeHash(hash);
            log.info("Single-use invite code consumed and deleted: codeHash={}, chatId={}", hash, invite.getChatId());
        }

        // ========== 步骤7: 返回聊天室信息 ==========
        return info;
    }

}