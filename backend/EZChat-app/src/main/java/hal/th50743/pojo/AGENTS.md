# POJO Layer

**Purpose**: Data transfer objects, view objects, entities, request/response types.

## STRUCTURE

```
pojo/
├── req/              # Request DTOs
│   ├── LoginReq.java
│   ├── RegisterReq.java
│   └── ...
├── vo/               # View Objects (responses)
│   ├── ChatVO.java
│   ├── MessageVO.java
│   └── ...
├── Result.java       # Generic API response wrapper
├── ErrorCode.java    # Error code enum
├── User.java         # Entity
├── Chat.java         # Entity
├── Message.java      # Entity
└── ...               # Other entities
```

## CONVENTIONS

**Result wrapper** (ALL endpoints):
```java
Result<T>  // { status: 1|0, data: T, msg: string }
Result.ok(data)
Result.error(ErrorCode.XXX)
```

**Request types**: `XxxReq.java` in `req/`
**Response types**: `XxxVO.java` in `vo/`

**Entity → VO**: Use `assembler/XxxAssembler.java`

**Lombok**:
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class XxxReq {
```

## NAMING

| Type | Pattern | Example |
|------|---------|---------|
| Request | `{Action}Req` | `LoginReq`, `CreateChatReq` |
| Response | `{Entity}VO` | `ChatVO`, `MessageVO` |
| Entity | `{Name}` | `User`, `Chat`, `Message` |

## ANTI-PATTERNS

- Expose entity directly → Use VO
- Complex logic in POJO → Move to Service
- Missing validation annotations → Add `@NotNull` etc
