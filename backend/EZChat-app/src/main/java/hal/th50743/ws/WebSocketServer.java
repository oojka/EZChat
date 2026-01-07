package hal.th50743.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import hal.th50743.pojo.MessageReq;
import hal.th50743.pojo.MessageVO;
import hal.th50743.pojo.UserStatus;
import hal.th50743.service.ChatService;
import hal.th50743.service.MessageService;
import hal.th50743.service.UserService;
import hal.th50743.utils.JwtUtils;
import hal.th50743.utils.MessageUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 服务端点
 * 优化记录：
 * 1. ObjectMapper 改为静态常量，避免每个连接重复创建。
 * 2. OnOpen 增加 Token 过期捕获，返回 4001 状态码。
 * 3. 消息广播增加异常隔离，防止单点失败影响全体。
 */
@Slf4j
@ServerEndpoint(value = "/websocket/{token}")
@Component
public class WebSocketServer {

    // --- 实例成员变量 (每个连接独有) ---
    private Integer userId;
    private String uid;
    private List<Integer> broadcastList;
    private String currentChatCode;

    // --- 静态成员变量 (全局共享) ---
    private static final Map<Integer, Session> onLineUsers = new ConcurrentHashMap<>();

    // 优化：ObjectMapper 是线程安全的且开销大，应设为 static final 全局共享
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // --- Service 注入 (静态) ---
    private static JwtUtils jwtUtils;
    private static UserService userService;
    private static ChatService chatService;
    private static MessageService messageService;

    @Autowired
    public void setServices(JwtUtils jwtUtils, UserService userService,
            ChatService chatService, MessageService messageService) {
        WebSocketServer.jwtUtils = jwtUtils;
        WebSocketServer.userService = userService;
        WebSocketServer.chatService = chatService;
        WebSocketServer.messageService = messageService;
    }

    /**
     * 连接建立
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("token") String token) {
        try {
            // 1. 安全解析 Token (修复：增加异常捕获)
            Claims claims = jwtUtils.parseJwt(token);
            this.uid = claims.get("uid", String.class);
            this.userId = Integer.valueOf(userService.getIdByUid(this.uid).toString());

            // 2. 存入在线列表
            onLineUsers.put(this.userId, session);

            // 3. 获取联系人并广播上线
            // 优化：异步或快速获取，避免阻塞握手，这里暂保持原逻辑
            this.broadcastList = chatService.getChatMembers(this.userId);

            UserStatus u = new UserStatus(this.uid, true, LocalDateTime.now());
            String message = MessageUtils.setMessage(2001, "USER_STATUS", u);

            // 广播时不包含自己，但 OnOpen 成功不需要给自己发消息，只广播给好友
            send(message, broadcastList);

        } catch (ExpiredJwtException e) {
            log.warn("WS连接拒绝: Token已过期");
            closeSession(session, 4001, "Token Expired");
        } catch (Exception e) {
            log.error("WS连接异常", e);
            closeSession(session, 4002, "Authentication Failed");
        }
    }

    /**
     * 收到消息
     */
    @OnMessage
    public void onMessage(String rowMessage) {
        // 1. 心跳检测优化
        if (rowMessage.startsWith("PING")) {
            if (rowMessage.length() > 4) {
                // 只有携带了 chatCode 才更新，防止纯 PING 覆盖
                this.currentChatCode = rowMessage.substring(4);
            }
            // 回复 PONG (仅给自己)
            sendSelf(onLineUsers.get(this.userId), "PONG");
            return;
        }

        try {
            // 2. 反序列化
            MessageReq msg = objectMapper.readValue(rowMessage, MessageReq.class);

            // 3. 校验
            if (!isValidMessage(msg)) {
                log.warn("收到无效消息: uid={}, content={}", this.uid, rowMessage);
                return;
            }

            log.info("收到消息: sender={}, chatCode={}", this.uid, msg.getChatCode());

            // 4. 业务处理
            List<Integer> sendList = messageService.handleWSMessage(this.userId, msg);

            // 5. 构建广播消息
            boolean hasText = msg.getText() != null && !msg.getText().isBlank();
            boolean hasImages = msg.getImages() != null && !msg.getImages().isEmpty();
            int messageType = hasText && hasImages ? 2 : (hasImages ? 1 : 0);

            MessageVO messageVO = new MessageVO(
                    this.uid,
                    msg.getChatCode(),
                    messageType,
                    msg.getText(),
                    null,
                    msg.getImages(),
                    LocalDateTime.now());

            // 6. 发送
            // 给目标群体发送聊天内容
            send(MessageUtils.setMessage(1001, "MESSAGE", messageVO), sendList);
            // 给自己发送 ACK (确认消息已达服务端)
            sendSelf(onLineUsers.get(this.userId), MessageUtils.setMessage(2002, "ACK", msg.getTempId()));

        } catch (JsonProcessingException e) {
            log.error("JSON解析失败: {}", rowMessage);
        } catch (Exception e) {
            log.error("消息处理异常", e);
            // 可以在此给前端发一个 ERROR 类型的消息
        }
    }

    /**
     * 连接关闭
     */
    @OnClose
    public void onClose(Session session) {
        if (this.userId == null) {
            log.warn("未认证连接关闭");
            return;
        }

        // 1. 优先移除 Session，防止后续消息误发
        onLineUsers.remove(this.userId);

        try {
            // 2. 更新最后在线时间
            userService.updateLastSeenAt(this.userId, this.currentChatCode, LocalDateTime.now());

            // 3. 重新获取一次联系人列表(确保准确性)，并广播下线
            // 注意：如果 chatService 依赖数据库，这里可能会有延迟，但在 onClose 中通常可接受
            this.broadcastList = chatService.getChatMembers(this.userId);

            if (this.broadcastList != null && !this.broadcastList.isEmpty()) {
                UserStatus u = new UserStatus(this.uid, false, LocalDateTime.now());
                send(MessageUtils.setMessage(2001, "USER_STATUS", u), this.broadcastList);
            }
            log.info("用户下线: {}", this.userId);

        } catch (Exception e) {
            log.error("用户下线清理逻辑异常: {}", this.userId, e);
        }
    }

    // --- 辅助方法 ---

    /**
     * 群发消息 (容错处理)
     */
    private void send(String message, List<Integer> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty())
            return;

        for (Integer targetId : targetUserIds) {
            // 跳过自己
            if (targetId.equals(this.userId))
                continue;

            Session targetSession = onLineUsers.get(targetId);
            if (targetSession != null && targetSession.isOpen()) {
                try {
                    // 加锁防止多线程写入同一 Session 冲突
                    synchronized (targetSession) {
                        targetSession.getBasicRemote().sendText(message);
                    }
                } catch (IOException e) {
                    log.error("消息发送失败: receiver={}, error={}", targetId, e.getMessage());
                    // 优化：单个发送失败不应抛出异常打断循环
                }
            }
        }
    }

    /**
     * 给自己发送消息
     */
    private void sendSelf(Session session, String message) {
        if (session != null && session.isOpen()) {
            try {
                synchronized (session) {
                    session.getBasicRemote().sendText(message);
                }
            } catch (IOException e) {
                log.error("自发送失败: uid={}, msg={}", this.uid, message);
            }
        }
    }

    /**
     * 辅助关闭 Session
     */
    private void closeSession(Session session, int code, String reason) {
        try {
            session.close(new CloseReason(() -> code, reason));
        } catch (IOException e) {
            // 忽略关闭时的错误
        }
    }

    private boolean isValidMessage(MessageReq msg) {
        return msg.getSender() != null && msg.getSender().equals(this.uid) &&
                msg.getChatCode() != null && !msg.getChatCode().isEmpty() &&
                (msg.getText() != null || (msg.getImages() != null && !msg.getImages().isEmpty()));
    }

    public static Map<Integer, Session> getOnLineUserList() {
        return onLineUsers;
    }

    /**
     * 静态广播方法：供 Service 层调用
     *
     * @param message       JSON 消息字符串
     * @param targetUserIds 接收者 ID 列表
     */
    public static void broadcast(String message, List<Integer> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty())
            return;

        for (Integer targetId : targetUserIds) {
            Session targetSession = onLineUsers.get(targetId);
            if (targetSession != null && targetSession.isOpen()) {
                try {
                    synchronized (targetSession) {
                        targetSession.getBasicRemote().sendText(message);
                    }
                } catch (IOException e) {
                    log.error("Broadcast failed: receiver={}, error={}", targetId, e.getMessage());
                }
            }
        }
    }
}