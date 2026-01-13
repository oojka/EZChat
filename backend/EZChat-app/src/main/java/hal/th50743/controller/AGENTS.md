# Controller 层

## OVERVIEW

REST API 端点，薄层设计，仅负责 HTTP 处理，业务逻辑委托 Service。

## FILES

| 文件 | 路径前缀 | 职责 |
|------|----------|------|
| `AuthController` | `/auth` | 登录/注册/访客/刷新 Token |
| `ChatController` | `/chat` | 聊天室 CRUD/加入/离开 |
| `MessageController` | `/message` | 消息列表/上传图片 |
| `UserController` | `/user` | 用户资料/修改密码 |
| `FriendController` | `/friend` | 好友系统 |
| `AssetController` | `/media` | 图片上传/检查去重 |
| `AppInitController` | `/app` | 应用初始化数据 |

## CONVENTIONS

### 返回格式

所有接口返回 `Result<T>`：

```java
@GetMapping("/list")
public Result<List<ChatVO>> list() {
    return Result.ok(chatService.getList());
}
```

### 参数校验

```java
@PostMapping
public Result<Void> create(@RequestBody @Valid CreateReq req) {
    // @Valid 触发校验，失败由 GlobalExceptionHandler 处理
}
```

### 当前用户

```java
Integer userId = CurrentHolder.getUserId();  // 从 ThreadLocal 获取
```

## ANTI-PATTERNS

- ❌ 业务逻辑 (移到 Service)
- ❌ 直接调用 Mapper
- ❌ 返回 Entity (应转 VO)
- ❌ try-catch 处理业务异常
