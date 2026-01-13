# POJO 层

## OVERVIEW

数据传输对象、视图对象、实体类、请求/响应类型。55 个文件。

## STRUCTURE

```
pojo/
├── *Req.java        # 请求 DTO (LoginReq, ChatReq...)
├── *VO.java         # 视图对象 (ChatVO, MessageVO...)
├── *.java           # 实体类 (User, Chat, Message...)
├── Result.java      # 统一响应包装器
└── ErrorCode.java   # 错误码枚举 (361 行)
```

## CONVENTIONS

### Result 包装器 (所有接口必须)

```java
Result<T>  // { status: 1|0, data: T, msg: string }
Result.ok(data)
Result.error(ErrorCode.XXX)
```

### 命名规则

| 类型 | 模式 | 示例 |
|------|------|------|
| 请求 | `{Action}Req` | `LoginReq`, `ChatReq` |
| 响应 | `{Entity}VO` | `ChatVO`, `MessageVO` |
| 实体 | `{Name}` | `User`, `Chat`, `Message` |

### Lombok 注解

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class XxxReq {
```

### Entity → VO 转换

使用 `assembler/XxxAssembler.java`，禁止直接返回 Entity。

## KEY CLASSES

| 类 | 类型 | 说明 |
|---|---|---|
| `Result<T>` | Wrapper | 统一响应 `{status, data, msg}` |
| `User` | Entity | 用户表，含访客/正式用户 |
| `Message` | Entity | 消息表，seqId 用于排序 |
| `MessageVO` | VO | 消息视图，含 sender(uid) |
| `ChatVO` | VO | 聊天室视图，含成员列表 |
| `Image` | DTO | 图片信息 (url, thumbUrl, assetId) |
| `ErrorCode` | Enum | 所有错误码定义 |

## IMMUTABLE FIELDS (可安全缓存)

`MessageVO` 中以下字段不可变：
- `sender` (uid)
- `chatCode`
- `seqId`
- `text`
- `assetIds`
- `createTime`

## ANTI-PATTERNS

- ❌ 直接返回 Entity 给 Controller → 使用 VO
- ❌ POJO 中写复杂逻辑 → 移到 Service
- ❌ 缺少校验注解 → 添加 `@NotNull` 等
