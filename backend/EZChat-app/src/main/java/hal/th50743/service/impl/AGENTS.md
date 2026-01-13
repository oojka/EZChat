# Service 实现层

## OVERVIEW

所有业务逻辑的实现类，遵循接口-实现分离模式。

## FILES

| 文件 | 行数 | 职责 |
|------|------|------|
| `ChatServiceImpl` | 629 | 聊天室 CRUD、加入/离开、权限 |
| `AssetServiceImpl` | 573 | 图片上传、去重、缩略图、MinIO |
| `AuthServiceImpl` | 541 | 登录/注册/访客、双 Token |
| `FriendServiceImpl` | 342 | 好友申请/接受/列表 |
| `UserServiceImpl` | 296 | 用户资料 CRUD |
| `MessageServiceImpl` | 274 | 消息存储/查询/seqId 分页 |
| `ChatInviteServiceImpl` | 261 | 邀请码生成/验证 |
| `FormalUserServiceImpl` | 216 | 正式用户凭证/密码 |
| `GuestCleanupService` | - | 定时清理离线访客 |
| `AsyncLogService` | - | 异步操作审计日志 |
| `CacheServiceImpl` | - | Caffeine Token 缓存 |
| `PresenceServiceImpl` | - | 在线状态管理 |

## PATTERNS

### 标准模板

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class XxxServiceImpl implements XxxService {
    private final XxxMapper xxxMapper;
    private final OtherService otherService;  // 可依赖其他 Service
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createXxx(...) {
        // 1. 校验
        // 2. 业务逻辑
        // 3. 持久化
        log.info("Created xxx: id={}", id);
    }
}
```

### 事务边界

- 单表操作：通常不需要 `@Transactional`
- 多表操作：必须加 `@Transactional(rollbackFor = Exception.class)`
- 只读操作：可加 `@Transactional(readOnly = true)`

### 错误处理

```java
// ✅ 正确
if (user == null) {
    log.error("User not found: uid={}", uid);
    throw new BusinessException(ErrorCode.USER_NOT_FOUND);
}

// ❌ 错误 - 不要捕获 BusinessException
try {
    userService.getUser(uid);
} catch (BusinessException e) {  // 禁止
    // ...
}
```

## ANTI-PATTERNS

- HTTP 相关逻辑 (Request/Response 处理)
- 直接返回 Entity (应转 VO)
- 循环依赖 (用 `@Lazy` 解决)
