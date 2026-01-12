# EZChat Project Knowledge Base

**Generated:** 2026-01-11 19:05 JST  
**Commit:** fdc41ec  
**Branch:** master

## OVERVIEW

Real-time chat system: Spring Boot 3.3.4 + Vue 3.5 + WebSocket + MySQL + MinIO. Features: friend system, private chat, guest/formal auth, i18n (5 langs), image dedup.

## STRUCTURE

```
EZChat/
├── backend/
│   ├── EZChat-parent/           # Maven aggregator
│   ├── EZChat-app/              # Main Spring Boot app
│   │   └── src/main/java/hal/th50743/
│   │       ├── controller/      # REST endpoints (thin)
│   │       ├── service/         # Business logic ★
│   │       ├── mapper/          # MyBatis DAOs
│   │       ├── pojo/            # DTOs, VOs, entities
│   │       ├── ws/              # WebSocket server
│   │       └── assembler/       # Entity→VO converters
│   └── dependencies/            # Custom MinIO starter
└── frontend/vue-ezchat/
    └── src/
        ├── api/                 # Axios wrappers
        ├── composables/         # Vue hooks ★
        ├── stores/              # Pinia state ★
        ├── views/               # Pages
        ├── components/          # Reusable UI
        └── i18n/locales/        # zh/en/ja/ko/zh-tw
```

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Add API endpoint | `controller/` + `service/impl/` | Thin controller pattern |
| Business logic | `service/impl/*ServiceImpl.java` | All logic here |
| Add Vue feature | `composables/useXxx.ts` | Extract from components |
| State management | `stores/*Store.ts` | Pinia with storeToRefs |
| Database schema | `resources/sql/init.sql` | MySQL 8 + utf8mb4 |
| API types | `frontend/.../type/index.ts` | Global type definitions |
| WebSocket | `ws/WebSocketServer.java` | Jakarta WebSocket |

## COMMANDS

```bash
# Frontend
cd frontend/vue-ezchat
npm run dev          # Dev server :5173
npm run build        # Type check + build
npm run type-check   # TS only
npm run lint         # ESLint fix

# Backend
cd backend/EZChat-parent
mvn -q -pl ../EZChat-app spring-boot:run  # Run :8080
mvn test             # All tests
```

## CONVENTIONS

### TypeScript/Vue
- **Zero `any`**: Use `unknown` + type guards
- **No `as Type`**: Use control flow narrowing
- `<script setup lang="ts">` exclusively
- Components <300 lines → extract to composables
- API types: `{Name}ApiReq` pattern
- Use `@/` path alias

### Java/Spring
- **Layer separation**: Controller (HTTP) → Service (logic) → Mapper (DB)
- `@Slf4j @Service @RequiredArgsConstructor` on all services
- `@Transactional(rollbackFor = Exception.class)` - always specify
- `throw new BusinessException(ErrorCode.XXX)` for errors
- Javadoc in **Chinese (Simplified)**
- Logs in **English**

### Auth
- Header: `token: <jwt>` (not Authorization Bearer)
- JWT claim: `uid` (lowercase)
- WebSocket: `/websocket/{token}`
- Dual token: Access (memory) + Refresh (localStorage)

## ANTI-PATTERNS (FORBIDDEN)

- `any` type in TypeScript
- Business logic in controllers
- `@Transactional` on interface (only impl)
- Catching BusinessException (let GlobalExceptionHandler handle)
- Direct API calls in .vue files (use composables/stores)
- `as Type` assertions without guards

## ERROR CODES

| Range | Domain |
|-------|--------|
| 40xxx | Client |
| 41xxx | User |
| 42xxx | Chat |
| 43xxx | File |
| 50xxx | Server |

## PROXY CONFIG

- Frontend `/api/*` → `localhost:8080/*` (strips /api)
- WebSocket `/websocket/*` → `ws://localhost:8080/websocket/*`

## SUBDIRECTORY DOCS

| Path | Focus |
|------|-------|
| `frontend/.../composables/AGENTS.md` | Vue hooks patterns |
| `frontend/.../stores/AGENTS.md` | Pinia state patterns |
| `backend/.../service/AGENTS.md` | Service layer conventions |
| `backend/.../pojo/AGENTS.md` | DTO/VO/Entity patterns |

## NOTES

- No .env auto-load: Set `DB_*`, `JWT_*`, `OSS_*` env vars manually
- IntelliJ sync: `Ctrl+Alt+Y` after backend changes
- Image upload: Frontend pre-compress → Backend normalize → MinIO
- Dual-hash dedup: raw hash (frontend) + normalized hash (backend)

## CODE COMMENT STANDARDS (Batch 3)

We have established strict comment standards for the codebase to ensure maintainability and clarity.

### 1. General Principles
- **Language**: Comments in **Chinese (Simplified)**.
- **Logs**: Log messages in **English** (for server compatibility).
- **Style**: Use Javadoc/Kdoc style (`/** ... */`) for all public interfaces (Classes, Methods, Fields, Components, Props).

### 2. Backend (Java)
- **Class Level**: Describe the class responsibility.
- **Field Level**: Explain the field meaning, especially business rules (e.g., status codes, default values).
- **Method Level**: Describe logic, `@param`, `@return`, and potential exceptions.

**Example (`ChatReq.java`):**
```java
/**
 * 聊天室代码（8位数字，可选）
 * <p>
 * 业务规则：
 * - 如果提供：使用指定的代码（需确保唯一性）
 * - 如果为空：后端自动生成8位数字代码
 */
private String chatCode;
```

### 3. Frontend (Vue/TS)
- **Component Level**: Top-level comment in `<script setup>` describing:
  - Core functionality
  - Routing parameters
  - Layout structure
  - Key dependencies
- **Props/Types**: Document all props and complex types.
- **Complex Logic**: Explain non-obvious logic steps.

**Example (`index.vue`):**
```typescript
/**
 * 聊天页面组件
 *
 * 核心功能：
 * - 消息列表展示与实时接收
 * - 消息发送（文本/图片）
 */
```

### 4. Coverage Status (Batch 3)
- **POJO/DTO**: 100% Complete
- **Exceptions**: 100% Complete
- **Views/Components**: ~45% Complete (Key core views done)

