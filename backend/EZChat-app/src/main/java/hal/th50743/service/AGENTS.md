# Service 层

## OVERVIEW

所有业务逻辑。Controller 是薄层，仅处理 HTTP。

## STRUCTURE

```
service/
├── AuthService.java          # 登录/注册/访客/Token
├── UserService.java          # 用户 CRUD/资料
├── FormalUserService.java    # 凭证/密码
├── ChatService.java          # 聊天室 CRUD/加入/离开
├── ChatInviteService.java    # 邀请码
├── MessageService.java       # 消息/seqId 分页
├── AssetService.java         # 图片上传/去重
├── FriendService.java        # 好友系统
├── PresenceService.java      # 在线状态
├── CacheService.java         # Caffeine 缓存
└── impl/                     # ★ 实现类 (见 impl/AGENTS.md)
```

## WHERE TO LOOK

| 任务 | 文件 |
|------|------|
| 认证/Token | `impl/AuthServiceImpl.java` |
| 聊天室操作 | `impl/ChatServiceImpl.java` |
| 图片处理 | `impl/AssetServiceImpl.java` |
| 好友系统 | `impl/FriendServiceImpl.java` |
| 密码管理 | `impl/FormalUserServiceImpl.java` |
| 消息处理 | `impl/MessageServiceImpl.java` |

## CONVENTIONS

### 类注解

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class XxxServiceImpl implements XxxService {
```

### 事务

```java
@Transactional(rollbackFor = Exception.class)
public void create(...) {
```

### 错误处理

```java
throw new BusinessException(ErrorCode.USER_NOT_FOUND);
throw new BusinessException(ErrorCode.FORBIDDEN, "message");
```

### 日志

- `log.info()` - 业务操作
- `log.error()` - 抛异常前

### Javadoc

中文 (简体) 必须

## LAYER RULES

| 层 | 可调用 | 禁止 |
|---|---|---|
| Service | Mapper, Service, Assembler | Controller, WS |

## ANTI-PATTERNS

- ❌ Service 中处理 HTTP (Request/Response)
- ❌ `@Transactional` 标在接口上
- ❌ 捕获 BusinessException
- ❌ 返回 Entity 给 Controller → 使用 Assembler 转 VO
