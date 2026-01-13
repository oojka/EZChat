# WebSocket 服务

## OVERVIEW

Jakarta WebSocket 实现，822 行核心代码，处理实时消息广播、心跳、ACK。

## ENTRY POINT

```java
@ServerEndpoint(value = "/websocket")
public class WebSocketServer {
```

## CONNECTION

前端连接：`ws://host:8080/websocket`
认证：连接后首条消息发送 Token

## MESSAGE TYPES

| 类型 | 方向 | 说明 |
|------|------|------|
| `auth` | C→S | 认证 Token |
| `message` | C→S | 发送消息 |
| `ack` | S→C | 消息确认 |
| `ping` | C→S | 心跳 |
| `pong` | S→C | 心跳响应 |
| `broadcast` | S→C | 消息广播 |
| `presence` | S→C | 上下线通知 |

## KEY METHODS

| 方法 | 触发 |
|------|------|
| `onOpen` | 连接建立 |
| `onMessage` | 收到消息 |
| `onClose` | 连接关闭 |
| `onError` | 错误处理 |
| `broadcast` | 群发消息 |

## NOTES

- 消息先存 DB，再广播
- 用户下线有 30s 防抖，避免网络波动误报
- seqId 保证消息顺序
