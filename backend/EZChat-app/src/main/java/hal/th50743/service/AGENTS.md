# Service Layer

**Purpose**: All business logic. Controllers are thin HTTP handlers only.

## STRUCTURE

```
service/
├── AuthService.java          # Login, register, guest, token
├── UserService.java          # User CRUD, profile
├── FormalUserService.java    # Credentials, password
├── ChatService.java          # Room CRUD, join/leave
├── ChatInviteService.java    # Invite codes
├── MessageService.java       # Messages, seq_id pagination
├── AssetService.java         # Image upload, dedup
├── FriendService.java        # Friend requests
├── PresenceService.java      # Online status
├── TokenCacheService.java    # JWT cache (Caffeine)
└── impl/
    ├── *ServiceImpl.java     # Implementations
    ├── GuestCleanupService.java  # Scheduled cleanup
    └── AsyncLogService.java  # Audit logging
```

## WHERE TO LOOK

| Task | File |
|------|------|
| Auth | `AuthServiceImpl.java` |
| Room ops | `ChatServiceImpl.java` |
| Images | `AssetServiceImpl.java` |
| Friends | `FriendServiceImpl.java` |
| Password | `FormalUserServiceImpl.java` |

## CONVENTIONS

**Class annotations**:
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class XxxServiceImpl implements XxxService {
```

**Transactions**:
```java
@Transactional(rollbackFor = Exception.class)
public void create(...) {
```

**Errors**:
```java
throw new BusinessException(ErrorCode.USER_NOT_FOUND);
throw new BusinessException(ErrorCode.FORBIDDEN, "message");
```

**Logging**: `log.info()` business ops, `log.error()` before throw

**Javadoc**: Chinese (Simplified) required

## LAYER RULES

| Layer | Can Use | Forbidden |
|-------|---------|-----------|
| Service | Mapper, Service, Assembler | Controller, WS |

## ANTI-PATTERNS

- HTTP handling in service
- `@Transactional` on interface
- Catching BusinessException
- Return entity to controller → Use Assembler
