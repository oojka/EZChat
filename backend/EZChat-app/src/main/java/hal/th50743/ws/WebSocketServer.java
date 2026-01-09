package hal.th50743.ws;

// JSON 序列化/反序列化相关
import com.fasterxml.jackson.core.JsonProcessingException; // JSON 处理异常
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 对象映射器
import com.fasterxml.jackson.databind.SerializationFeature; // 序列化特性配置
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule; // Java 8 时间模块支持

// 项目自定义数据对象
import hal.th50743.pojo.MessageReq; // 消息请求对象
import hal.th50743.pojo.MessageVO; // 消息视图对象
import hal.th50743.pojo.UserStatus; // 用户状态对象

// 服务层接口
import hal.th50743.service.ChatService; // 聊天服务
import hal.th50743.service.MessageService; // 消息服务
import hal.th50743.service.TokenCacheService; // Token 缓存服务
import hal.th50743.service.UserService; // 用户服务

// 工具类
import hal.th50743.utils.JwtUtils; // JWT 工具类
import hal.th50743.utils.MessageUtils; // 消息工具类

// JWT 相关
import io.jsonwebtoken.Claims; // JWT 声明
import io.jsonwebtoken.ExpiredJwtException; // JWT 过期异常

// WebSocket 相关
import jakarta.websocket.*; // WebSocket 核心注解
import jakarta.websocket.server.PathParam; // 路径参数注解
import jakarta.websocket.server.ServerEndpoint; // WebSocket 服务端点注解

// Lombok 日志
import lombok.extern.slf4j.Slf4j; // SLF4J 日志注解

// Spring 相关
import org.springframework.beans.factory.annotation.Autowired; // 依赖注入注解
import org.springframework.stereotype.Component; // 组件注解

// Java 标准库
import java.io.EOFException; // EOF 异常
import java.io.IOException; // IO 异常
import java.time.LocalDateTime; // 本地日期时间
import java.util.List; // 列表接口
import java.util.Map; // 映射接口
import java.util.concurrent.*; // 并发工具包

/**
 * WebSocket 服务端点 - 实时通信核心组件
 * 
 * <p>
 * 本类负责处理所有 WebSocket 连接的生命周期管理、消息路由和状态同步。
 * 采用单例模式设计，所有连接共享静态资源，确保线程安全和资源高效利用。
 * </p>
 * 
 * <h3>核心职责：</h3>
 * <ul>
 * <li>连接管理：处理用户连接、断开和重连</li>
 * <li>消息路由：接收、验证、处理和转发聊天消息</li>
 * <li>状态同步：实时同步用户在线/离线状态给相关联系人</li>
 * <li>心跳检测：维持连接活跃，检测网络状态</li>
 * </ul>
 * 
 * <h3>设计特点：</h3>
 * <ul>
 * <li><b>防抖机制</b>：30秒缓冲期防止网络波动导致的误判</li>
 * <li><b>线程安全</b>：使用 ConcurrentHashMap 管理在线用户</li>
 * <li><b>资源优化</b>：静态共享 ObjectMapper 减少内存开销</li>
 * <li><b>容错处理</b>：单点发送失败不影响整体广播</li>
 * </ul>
 * 
 * <h3>优化记录：</h3>
 * <ol>
 * <li>ObjectMapper 改为静态常量，避免每个连接重复创建。</li>
 * <li>OnOpen 增加 Token 过期捕获，返回 4001 状态码。</li>
 * <li>消息广播增加异常隔离，防止单点失败影响全体。</li>
 * </ol>
 * 
 * @author 系统开发者
 * @since 1.0
 * @see jakarta.websocket.ServerEndpoint
 * @see org.springframework.stereotype.Component
 */
@Slf4j
@ServerEndpoint(value = "/websocket/{token}")
@Component
public class WebSocketServer {

    // ============================================
    // 实例成员变量 (每个 WebSocket 连接独有)
    // ============================================

    /** 用户ID - 数据库中的主键标识 */
    private Integer userId;

    /** 用户唯一标识符 - 业务层面的用户标识 */
    private String uid;

    /** 广播列表 - 当前用户需要广播消息的联系人ID列表 */
    private List<Integer> broadcastList;

    /** 当前聊天室代码 - 用户最后活跃的聊天室标识 */
    private String currentChatCode;

    // ============================================
    // 静态成员变量 (全局共享，所有连接共用)
    // ============================================

    /**
     * 在线用户映射表 - 线程安全的用户会话管理
     * Key: 用户ID (Integer)
     * Value: WebSocket 会话对象 (Session)
     * 用途：快速查找用户的 WebSocket 连接，用于消息推送
     */
    private static final Map<Integer, Session> onLineUsers = new ConcurrentHashMap<>();

    /**
     * 离线延迟任务映射表 - 管理用户的离线缓冲任务
     * Key: 用户ID (Integer)
     * Value: 调度任务句柄 (ScheduledFuture<?>)
     * 用途：记录每个用户的离线缓冲任务，支持重连时取消
     */
    private static final Map<Integer, ScheduledFuture<?>> offlineTasks = new ConcurrentHashMap<>();

    /**
     * 单线程调度器 - 专门处理离线广播延迟任务
     * 设计考虑：使用单线程避免多线程竞争，确保任务顺序执行
     * 线程池类型：ScheduledThreadPoolExecutor
     */
    private static final ScheduledExecutorService scheduledExecutorService = Executors
            .newSingleThreadScheduledExecutor();

    /**
     * 离线广播延迟时间（秒）- 防抖机制核心参数
     * 业务意义：用户断开连接后，等待30秒再确认为真正下线
     * 优化点：平衡用户体验（快速反馈）和网络容错（避免误判）
     */
    private static final int OFFLINE_BROADCAST_DELAY_SECONDS = 30;

    /**
     * JSON 对象映射器 - 线程安全的全局共享实例
     * 优化说明：
     * 1. ObjectMapper 创建成本高，设为静态避免重复创建
     * 2. 注册 JavaTimeModule 支持 Java 8 时间类型序列化
     * 3. 禁用时间戳格式，使用 ISO-8601 标准格式
     */
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // ============================================
    // Service 静态注入 (Spring 管理的单例服务)
    // ============================================

    /** JWT 工具类 - 负责 Token 解析和验证 */
    private static JwtUtils jwtUtils;

    /** 用户服务 - 处理用户相关业务逻辑 */
    private static UserService userService;

    /** 聊天服务 - 处理聊天室和联系人关系 */
    private static ChatService chatService;

    /** 消息服务 - 处理消息的存储和业务逻辑 */
    private static MessageService messageService;

    /** Token 缓存服务 - 校验 AccessToken 有效性 */
    private static TokenCacheService tokenCacheService;

    /**
     * Service 依赖注入方法
     * 
     * <p>
     * 由于 WebSocket 端点不是由 Spring 直接管理，无法使用常规的 @Autowired 注解。
     * 通过此静态 setter 方法实现依赖注入，确保服务单例被正确初始化。
     * </p>
     * 
     * <p>
     * <b>注入时机</b>：Spring 容器启动时自动调用
     * </p>
     * <p>
     * <b>线程安全</b>：静态变量，所有连接共享同一服务实例
     * </p>
     * 
     * @param jwtUtils       JWT 工具类实例
     * @param userService    用户服务实例
     * @param chatService    聊天服务实例
     * @param messageService 消息服务实例
     * @param tokenCacheService Token 缓存服务实例
     */
    @Autowired
    public void setServices(JwtUtils jwtUtils, UserService userService,
            ChatService chatService, MessageService messageService, TokenCacheService tokenCacheService) {
        WebSocketServer.jwtUtils = jwtUtils;
        WebSocketServer.userService = userService;
        WebSocketServer.chatService = chatService;
        WebSocketServer.messageService = messageService;
        WebSocketServer.tokenCacheService = tokenCacheService;
    }

    /**
     * WebSocket 连接建立处理方法
     * 
     * <p>
     * 当客户端发起 WebSocket 连接请求时触发此方法。主要职责包括：
     * </p>
     * <ol>
     * <li>Token 验证和用户身份识别</li>
     * <li>重连检测和防抖处理</li>
     * <li>用户状态初始化和广播</li>
     * <li>异常处理和连接拒绝</li>
     * </ol>
     * 
     * <p>
     * <b>重连机制</b>：检查是否有 pending 的离线任务，如果有且能取消，则视为网络波动重连，
     * 不广播上线状态，避免频繁的状态切换通知。
     * </p>
     * 
     * <p>
     * <b>安全考虑</b>：Token 过期会返回 4001 状态码，认证失败返回 4002 状态码，
     * 前端可根据状态码进行相应的错误处理。
     * </p>
     * 
     * @param session WebSocket 会话对象，包含连接信息和通信通道
     * @param token   JWT 认证令牌，从路径参数中获取
     * @throws ExpiredJwtException Token 过期异常
     * @throws Exception           其他认证或业务异常
     * 
     * @see jakarta.websocket.OnOpen
     * @see io.jsonwebtoken.ExpiredJwtException
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("token") String token) {
        try {
            // ========== 步骤1: Token 解析和用户身份识别 ==========
            // 安全解析 JWT Token，提取用户信息
            Claims claims = jwtUtils.parseJwt(token);
            String tokenType = claims.get("tokenType", String.class);
            if (!"access".equals(tokenType)) {
                log.warn("WS连接拒绝: Token 类型不合法 tokenType={}", tokenType);
                closeSession(session, 4002, "Authentication Failed");
                return;
            }
            this.uid = claims.get("uid", String.class); // 获取用户唯一标识
            this.userId = Integer.valueOf(userService.getIdByUid(this.uid).toString()); // 转换为数据库用户ID

            String cachedToken = tokenCacheService.getAccessToken(this.userId);
            if (cachedToken == null || !cachedToken.equals(token)) {
                log.warn("WS连接拒绝: AccessToken 缓存校验失败 userId={}", this.userId);
                closeSession(session, 4002, "Authentication Failed");
                return;
            }

            // ========== 步骤2: 用户会话管理 ==========
            // 将用户会话存入全局在线用户映射表，后续消息推送依赖此表
            onLineUsers.put(this.userId, session);

            // ========== 步骤3: 重连检测和防抖处理 ==========
            // 【核心逻辑】检查是否有正在进行的"离线倒计时"任务
            ScheduledFuture<?> pendingTask = offlineTasks.remove(this.userId);
            boolean isReconnection = false; // 重连标志位

            if (pendingTask != null) {
                // 如果能成功取消任务，说明用户是在30秒缓冲期内回来的
                // cancel(false) 参数说明：false 表示如果任务已经开始执行，则不中断
                if (pendingTask.cancel(false)) {
                    isReconnection = true; // 标记为网络波动重连
                    log.info("用户 {} 在缓冲期内重连 (网络波动)，取消下线广播", this.userId);
                }
            }

            // ========== 步骤4: 获取联系人列表 ==========
            // 查询用户的所有聊天成员（好友/群聊成员）
            // 优化建议：此处可考虑异步获取，避免阻塞 WebSocket 握手过程
            this.broadcastList = chatService.getChatMembers(this.userId);

            // ========== 步骤5: 状态广播决策 ==========
            // 只有当"不是"快速重连时，才广播上线状态
            // 业务逻辑：网络波动导致的短暂断开不应触发状态变更通知
            if (!isReconnection) {
                UserStatus u = new UserStatus(this.uid, true, LocalDateTime.now());
                String message = MessageUtils.setMessage(2001, "USER_STATUS", u);
                send(message, broadcastList); // 广播上线状态给所有联系人
                // 注意：广播时不包含自己，OnOpen 成功不需要给自己发消息
            }

        } catch (ExpiredJwtException e) {
            // Token 过期异常处理：记录日志并关闭连接，返回特定状态码
            log.warn("WS连接拒绝: Token已过期", e);
            closeSession(session, 4001, "Token Expired");
        } catch (Exception e) {
            // 其他认证或业务异常处理
            log.error("WS连接异常", e);
            closeSession(session, 4002, "Authentication Failed");
        }
    }

    /**
     * WebSocket 消息接收处理方法
     * 
     * <p>
     * 当客户端通过 WebSocket 发送消息时触发此方法。处理流程：
     * </p>
     * <ol>
     * <li>心跳检测处理（PING/PONG 机制）</li>
     * <li>消息反序列化和验证</li>
     * <li>业务逻辑处理（消息存储、关系验证等）</li>
     * <li>消息类型判断和视图对象构建</li>
     * <li>消息广播和确认回执</li>
     * </ol>
     * 
     * <p>
     * <b>消息类型说明</b>：
     * </p>
     * <ul>
     * <li>类型 0: 纯文本消息</li>
     * <li>类型 1: 纯图片消息</li>
     * <li>类型 2: 图文混合消息</li>
     * </ul>
     * 
     * <p>
     * <b>确认机制</b>：每条消息处理成功后，会给发送者发送 ACK 确认，
     * 前端可根据 ACK 更新消息发送状态。
     * </p>
     * 
     * @param rowMessage 原始消息字符串，可能是 JSON 格式或心跳消息
     * @throws JsonProcessingException JSON 解析异常
     * @throws Exception               业务处理异常
     * 
     * @see jakarta.websocket.OnMessage
     * @see com.fasterxml.jackson.core.JsonProcessingException
     */
    @OnMessage
    public void onMessage(String rowMessage) {
        // ========== 步骤1: 心跳检测处理 ==========
        // 心跳机制：前端定期发送 PING 维持连接，服务端回复 PONG
        if (rowMessage.startsWith("PING")) {
            if (rowMessage.length() > 4) {
                // 心跳消息可能携带当前聊天室代码，用于更新用户最后活跃位置
                // 只有携带了 chatCode 才更新，防止纯 PING 覆盖现有值
                this.currentChatCode = rowMessage.substring(4);
            }
            // 回复 PONG (仅给发送者自己)，确认连接活跃
            sendSelf(onLineUsers.get(this.userId), "PONG");
            return; // 心跳消息不需要进一步处理
        }

        try {
            // ========== 步骤2: 消息反序列化 ==========
            // 将 JSON 字符串转换为 MessageReq 对象
            MessageReq msg = objectMapper.readValue(rowMessage, MessageReq.class);

            // ========== 步骤3: 消息有效性验证 ==========
            // 验证消息的发送者、聊天室和内容是否有效
            if (!isValidMessage(msg)) {
                log.warn("收到无效消息: uid={}, content={}", this.uid, rowMessage);
                return; // 无效消息直接丢弃，不处理
            }

            // 记录消息接收日志，用于监控和调试
            log.info("收到消息: sender={}, chatCode={}", this.uid, msg.getChatCode());

            // ========== 步骤4: 业务逻辑处理 ==========
            // 调用消息服务处理业务逻辑，返回处理结果（包含用户列表和 seqId）
            // 业务逻辑包括：消息存储、权限验证、关系检查等
            hal.th50743.pojo.WSMessageResult wsResult = messageService.handleWSMessage(this.userId, msg);
            List<Integer> sendList = wsResult.sendList();
            Long messageSeqId = wsResult.seqId();

            // ========== 步骤5: 构建广播消息视图对象 ==========
            // 判断消息类型：0-文本, 1-图片, 2-图文混合
            boolean hasText = msg.getText() != null && !msg.getText().isBlank();
            boolean hasImages = msg.getImages() != null && !msg.getImages().isEmpty();
            int messageType = hasText && hasImages ? 2 : (hasImages ? 1 : 0);

            // 构建消息视图对象，用于前端展示
            MessageVO messageVO = new MessageVO(
                    this.uid, // 发送者UID
                    msg.getChatCode(), // 聊天室代码
                    messageSeqId, // seqId (已填充)
                    messageType, // 消息类型
                    msg.getText(), // 文本内容
                    null, // 预留字段
                    msg.getImages(), // 图片列表
                    LocalDateTime.now()); // 服务器接收时间

            // ========== 步骤6: 消息发送和确认 ==========
            // 6.1 给目标用户群体发送聊天内容
            // 消息格式：状态码 1001 表示普通聊天消息
            send(MessageUtils.setMessage(1001, "MESSAGE", messageVO), sendList);

            // 6.2 给自己发送 ACK (确认消息已达服务端)
            // 业务意义：前端收到 ACK 后可将消息标记为"已发送"并更新 seqId
            // 消息格式：状态码 2002 表示确认回执
            hal.th50743.pojo.AckVO ackVO = hal.th50743.pojo.AckVO.builder()
                    .tempId(msg.getTempId())
                    .seqId(messageSeqId)
                    .build();
            sendSelf(onLineUsers.get(this.userId), MessageUtils.setMessage(2002, "ACK", ackVO));

        } catch (JsonProcessingException e) {
            // JSON 解析异常：客户端发送了非法格式的消息
            log.error("JSON解析失败: {}", rowMessage);
            // 可考虑给客户端发送错误响应，但当前设计是静默丢弃
        } catch (Exception e) {
            // 其他业务异常处理
            log.error("消息处理异常", e);
            // 优化建议：可以在此给前端发送 ERROR 类型的消息，通知处理失败
        }
    }

    /**
     * WebSocket 连接关闭处理方法
     * 
     * <p>
     * 当客户端断开 WebSocket 连接时触发此方法。采用防抖机制设计，
     * 避免网络波动导致的误判，提升用户体验。
     * </p>
     * 
     * <h3>业务逻辑流程：</h3>
     * <ol>
     * <li>用户断开 WebSocket 连接（浏览器关闭、网络中断、主动断开等）</li>
     * <li>立即从在线用户列表中移除 Session，防止后续消息误发</li>
     * <li>启动 30 秒缓冲倒计时，防止网络波动导致的误判</li>
     * <li>如果用户在缓冲期内重连，取消下线广播任务</li>
     * <li>如果缓冲期结束用户未重连，执行真正的下线逻辑</li>
     * </ol>
     * 
     * <h3>防抖机制设计：</h3>
     * <ul>
     * <li><b>目的</b>：避免网络波动（如短暂断网）导致频繁的上下线广播</li>
     * <li><b>实现</b>：使用 ScheduledExecutorService 延迟执行下线逻辑</li>
     * <li><b>重连检测</b>：用户在缓冲期内重连时取消延迟任务</li>
     * <li><b>缓冲时间</b>：30秒，平衡响应速度和网络容错</li>
     * </ul>
     * 
     * <p>
     * <b>线程安全考虑</b>：使用 final 局部变量捕获实例状态，确保延迟任务访问的数据一致性。
     * </p>
     * 
     * @param session WebSocket 会话对象
     * 
     * @see jakarta.websocket.OnClose
     * @see java.util.concurrent.ScheduledExecutorService
     */
    /**
     * WebSocket 错误处理方法
     * 
     * <p>
     * 当 WebSocket 生命周期中发生异常时触发此方法。
     * </p>
     * 
     * @param session 发生异常的会话
     * @param error   异常对象
     */
    @OnError
    public void onError(Session session, Throwable error) {
        // 忽略 EOFException，通常是客户端强制断开连接导致
        if (error instanceof EOFException) {
            log.debug("WebSocket EOFException (客户端断开): uid={}", this.uid);
            return;
        }

        log.error("WebSocket 发生错误: uid={}, error={}", this.uid, error.getMessage());
    }

    @OnClose
    public void onClose(Session session) {
        // ========== 步骤1: 安全检查 ==========
        // 处理未认证的连接（可能在 onOpen 阶段认证失败）
        if (this.userId == null) {
            log.warn("未认证连接关闭");
            return; // 未认证连接无需执行下线逻辑
        }

        // ========== 步骤2: 立即移除在线会话 ==========
        // 关键操作：立即从在线用户列表中移除，防止后续消息误发到已断开的连接
        // 即使后续用户重连，也会在 onOpen 中重新添加
        onLineUsers.remove(this.userId);

        // ========== 步骤3: 准备延迟任务数据 ====== ====
        // 使用 final 变量确保线程安全：延迟任务在另一个线程执行，需要捕获当前状态
        final Integer finalUserId = this.userId; // 用户ID
        final String finalUid = this.uid; // 用户唯一标识
        final String finalChatCode = this.currentChatCode; // 最后活跃聊天室

        log.info("用户 {} 断开连接，启动 {}秒 缓冲倒计时...", finalUserId, OFFLINE_BROADCAST_DELAY_SECONDS);

        // ========== 步骤4: 定义延迟下线任务 ==========
        // Runnable 任务：30秒后执行真正的下线逻辑
        Runnable offlineTask = () -> {
            try {
                // --- 缓冲期结束后执行的真正下线逻辑 ---

                // 4.1 更新数据库最后在线时间
                // 业务意义：记录用户最后活跃时间，用于前端显示"最后在线"状态
                userService.updateLastSeenAt(finalUserId, finalChatCode, LocalDateTime.now());

                // 4.2 重新获取用户的好友/群聊成员列表
                // 重要：不能使用 this.broadcastList（实例变量），因为 Runnable 在不同线程执行
                // 重新查询确保数据是最新的（用户关系可能在此期间发生变化）
                List<Integer> friends = chatService.getChatMembers(finalUserId);

                // 4.3 广播下线状态给所有联系人
                if (friends != null && !friends.isEmpty()) {
                    // 构建用户状态对象：uid、在线状态(false)、当前时间
                    UserStatus u = new UserStatus(finalUid, false, LocalDateTime.now());
                    // 使用 MessageUtils 封装为 WebSocket 消息
                    // 状态码 2001 表示用户状态更新，类型 "USER_STATUS"
                    String msg = MessageUtils.setMessage(2001, "USER_STATUS", u);

                    // 使用静态广播方法发送给所有好友
                    // 注意：广播方法会过滤不在线的用户，只发送给在线好友
                    broadcast(msg, friends);
                }
                log.info("用户 {} 缓冲期结束，确认为下线。", finalUserId);

            } catch (Exception e) {
                // 延迟任务异常处理：记录日志但不影响其他用户
                log.error("延迟下线任务异常: {}", finalUserId, e);
            } finally {
                // 4.4 任务结束，清理任务映射表
                // 无论成功与否，都需要移除任务记录，避免内存泄漏
                offlineTasks.remove(finalUserId);
            }
        };

        // ========== 步骤5: 提交延迟任务到调度器 ==========
        // 参数说明：
        // offlineTask - 要执行的任务
        // OFFLINE_BROADCAST_DELAY_SECONDS - 延迟时间（30秒）
        // TimeUnit.SECONDS - 时间单位（秒）
        ScheduledFuture<?> future = scheduledExecutorService.schedule(
                offlineTask,
                OFFLINE_BROADCAST_DELAY_SECONDS,
                TimeUnit.SECONDS);

        // ========== 步骤6: 保存任务句柄 ==========
        // 将任务句柄保存到映射表，以便用户在缓冲期内重连时取消任务
        offlineTasks.put(finalUserId, future);
    }

    // ============================================
    // 辅助方法区域
    // ============================================

    /**
     * 群发消息方法 (带容错处理)
     * 
     * <p>
     * 向指定的多个用户发送同一条消息。设计特点：
     * </p>
     * <ul>
     * <li><b>容错处理</b>：单个用户发送失败不影响其他用户</li>
     * <li><b>线程安全</b>：使用 synchronized 防止多线程写入冲突</li>
     * <li><b>跳过自己</b>：自动过滤发送者自己</li>
     * <li><b>连接检查</b>：只向在线且连接正常的用户发送</li>
     * </ul>
     * 
     * <p>
     * <b>性能考虑</b>：循环遍历目标用户列表，对于大规模广播可考虑优化为批量操作。
     * </p>
     * 
     * @param message       要发送的消息内容（JSON 格式字符串）
     * @param targetUserIds 目标用户ID列表，null 或空列表时直接返回
     * 
     * @throws IOException 单个用户发送失败时记录日志但不抛出异常
     */
    private void send(String message, List<Integer> targetUserIds) {
        // 参数检查：空列表直接返回，避免不必要的遍历
        if (targetUserIds == null || targetUserIds.isEmpty())
            return;

        // 遍历所有目标用户
        for (Integer targetId : targetUserIds) {
            // 跳过发送者自己：消息不需要发给自己
            if (targetId.equals(this.userId))
                continue;

            // 获取目标用户的 WebSocket 会话
            Session targetSession = onLineUsers.get(targetId);

            // 连接有效性检查：用户必须在线且连接正常
            if (targetSession != null && targetSession.isOpen()) {
                try {
                    // 线程安全：加锁防止多线程同时写入同一 Session 导致数据混乱
                    synchronized (targetSession) {
                        targetSession.getBasicRemote().sendText(message);
                    }
                } catch (IOException e) {
                    // 容错处理：单个用户发送失败只记录日志，不中断循环
                    // 可能原因：用户突然断开连接、网络异常等
                    log.error("消息发送失败: receiver={}, error={}", targetId, e.getMessage());
                }
            }
            // 注意：如果用户不在线，消息会被静默丢弃
            // 业务考虑：是否需要离线消息存储机制？
        }
    }

    /**
     * 给自己发送消息方法
     * 
     * <p>
     * 用于向消息发送者自己发送特定消息，主要场景：
     * </p>
     * <ul>
     * <li>心跳响应（PONG）</li>
     * <li>消息确认回执（ACK）</li>
     * <li>错误通知（ERROR）</li>
     * <li>状态同步（SYNC）</li>
     * </ul>
     * 
     * <p>
     * <b>线程安全</b>：使用 synchronized 确保同一连接的消息顺序发送。
     * </p>
     * <p>
     * <b>连接检查</b>：发送前验证会话是否仍然有效。
     * </p>
     * 
     * @param session 当前用户的 WebSocket 会话对象
     * @param message 要发送给自己的消息内容
     * 
     * @throws IOException 发送失败时记录错误日志
     */
    private void sendSelf(Session session, String message) {
        // 连接有效性检查：确保会话存在且连接正常
        if (session != null && session.isOpen()) {
            try {
                // 线程安全：加锁防止并发写入
                synchronized (session) {
                    session.getBasicRemote().sendText(message);
                }
            } catch (IOException e) {
                // 自发送失败：通常意味着连接已异常断开
                log.error("自发送失败: uid={}, msg={}", this.uid, message);
            }
        }
        // 如果会话无效，消息被静默丢弃
        // 业务考虑：是否需要重试机制或持久化存储？
    }

    /**
     * 安全关闭 WebSocket 会话方法
     * 
     * <p>
     * 用于在认证失败或异常情况下优雅地关闭连接，并向客户端返回特定的关闭原因。
     * </p>
     * 
     * <p>
     * <b>使用场景</b>：
     * </p>
     * <ul>
     * <li>Token 过期（code: 4001）</li>
     * <li>认证失败（code: 4002）</li>
     * <li>权限不足（code: 4003）</li>
     * <li>服务器错误（code: 5000）</li>
     * </ul>
     * 
     * <p>
     * <b>关闭码规范</b>：
     * </p>
     * <ul>
     * <li>4000-4999：客户端错误</li>
     * <li>5000-5999：服务器错误</li>
     * </ul>
     * 
     * @param session 要关闭的 WebSocket 会话
     * @param code    关闭状态码，用于前端识别关闭原因
     * @param reason  关闭原因描述，用于日志和调试
     * 
     * @see jakarta.websocket.CloseReason
     */
    private void closeSession(Session session, int code, String reason) {
        try {
            // 创建关闭原因对象，包含状态码和描述
            session.close(new CloseReason(() -> code, reason));
        } catch (IOException e) {
            // 忽略关闭时的 IO 异常：连接可能已经断开
            // 记录调试日志（可选）：log.debug("关闭会话时发生异常: {}", e.getMessage());
        }
    }

    /**
     * 消息有效性验证方法
     * 
     * <p>
     * 验证接收到的消息是否符合业务规则，防止非法或格式错误的消息进入处理流程。
     * </p>
     * 
     * <h3>验证规则：</h3>
     * <ol>
     * <li>发送者UID必须存在且与当前连接用户一致（防止消息伪造）</li>
     * <li>聊天室代码必须存在且非空（消息必须有归属的聊天室）</li>
     * <li>消息必须有内容：文本或图片至少包含一种</li>
     * </ol>
     * 
     * <p>
     * <b>安全考虑</b>：严格验证发送者身份，防止用户冒充他人发送消息。
     * </p>
     * 
     * @param msg 要验证的消息请求对象
     * @return true 如果消息有效，false 如果消息无效
     */
    private boolean isValidMessage(MessageReq msg) {
        return msg.getSender() != null && msg.getSender().equals(this.uid) && // 发送者验证
                msg.getChatCode() != null && !msg.getChatCode().isEmpty() && // 聊天室验证
                (msg.getText() != null || (msg.getImages() != null && !msg.getImages().isEmpty())); // 内容验证
    }

    /**
     * 获取在线用户列表（只读视图）
     * 
     * <p>
     * 提供当前所有在线用户的会话映射表，用于监控、调试或其他业务需求。
     * </p>
     * 
     * <p>
     * <b>线程安全</b>：返回的是 ConcurrentHashMap 的引用，调用者应注意并发访问安全。
     * </p>
     * <p>
     * <b>只读建议</b>：除非必要，调用者不应修改返回的映射表内容。
     * </p>
     * 
     * @return 在线用户映射表，Key为用户ID，Value为WebSocket会话
     * 
     * @see java.util.concurrent.ConcurrentHashMap
     */
    public static Map<Integer, Session> getOnLineUserList() {
        return onLineUsers;
    }

    /**
     * 静态广播方法：供 Service 层或其他组件调用
     * 
     * <p>
     * 全局消息广播接口，允许从任何地方向指定用户列表发送消息。
     * 常用于系统通知、状态同步等需要主动推送的场景。
     * </p>
     * 
     * <h3>使用场景：</h3>
     * <ul>
     * <li>系统公告推送</li>
     * <li>好友请求通知</li>
     * <li>群聊成员变更通知</li>
     * <li>后台任务完成通知</li>
     * </ul>
     * 
     * <p>
     * <b>设计特点</b>：
     * </p>
     * <ul>
     * <li>静态方法：无需 WebSocket 连接实例即可调用</li>
     * <li>容错处理：单个用户发送失败不影响其他用户</li>
     * <li>线程安全：使用 synchronized 防止会话写入冲突</li>
     * <li>连接检查：只向在线用户发送，离线用户静默忽略</li>
     * </ul>
     * 
     * @param message       要广播的 JSON 格式消息字符串
     * @param targetUserIds 目标用户ID列表，null 或空列表时直接返回
     * 
     * @throws IOException 单个用户发送失败时记录错误日志但不抛出异常
     * 
     * @see #send(String, List) 实例方法的静态版本
     */
    public static void broadcast(String message, List<Integer> targetUserIds) {
        // 参数有效性检查
        if (targetUserIds == null || targetUserIds.isEmpty())
            return;

        // 遍历所有目标用户
        for (Integer targetId : targetUserIds) {
            // 从全局在线用户映射表中获取会话
            Session targetSession = onLineUsers.get(targetId);

            // 连接有效性检查
            if (targetSession != null && targetSession.isOpen()) {
                try {
                    // 线程安全：加锁防止多线程同时写入同一会话
                    synchronized (targetSession) {
                        targetSession.getBasicRemote().sendText(message);
                    }
                } catch (IOException e) {
                    // 容错处理：记录错误但继续发送给其他用户
                    log.error("Broadcast failed: receiver={}, error={}", targetId, e.getMessage());
                }
            }
            // 注意：如果用户不在线，消息会被静默丢弃
            // 业务扩展：可考虑添加离线消息存储机制
        }
    }
}
