# EZChat Project Specification

## Project Overview

**EZChat** is a modern real-time chat system built with Spring Boot 3 + Vue 3, featuring WebSocket messaging, guest/registered authentication, presence tracking, image upload with thumbnails, internationalization, and dark mode.

---

## Tech Stack

### Backend

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Java | 17 |
| Framework | Spring Boot | 3.3.4 |
| WebSocket | Jakarta WebSocket + `@ServerEndpoint` | - |
| ORM | MyBatis | 3.0.3 |
| Pagination | PageHelper | 2.1.0 |
| Database | MySQL | 8.x |
| Authentication | JWT (jjwt) | 0.11.5 |
| Password Hashing | Spring Security Crypto (BCrypt) | 6.3.4 |
| Caching | Caffeine (Local Cache) | - |
| Object Storage | MinIO (custom starter: `minio-oss-spring-boot-starter`) | 0.0.5-SNAPSHOT |
| Image Processing | Thumbnailator | 0.4.20 |
| AOP | Spring AOP (Process Logging) | - |

### Frontend

| Component | Technology | Version |
|-----------|------------|---------|
| Framework | Vue | 3.5.25 |
| Language | TypeScript | 5.9.x |
| Build Tool | Vite | 7.2.x |
| State Management | Pinia | 3.0.x |
| Routing | Vue Router | 4.6.x |
| UI Library | Element Plus | 2.12.x |
| HTTP Client | Axios | 1.13.x |
| i18n | vue-i18n | 11.2.7 |
| Image Compression | browser-image-compression | 2.0.2 |
| JWT Decode | jwt-decode | 4.0.0 |

---

## Project Structure

```
EZChat/
├── backend/
│   ├── EZChat-parent/                         # Maven parent (aggregator)
│   ├── EZChat-app/                            # Spring Boot application
│   │   ├── src/main/java/hal/th50743/
│   │   │   ├── controller/                    # REST controllers
│   │   │   ├── service/                       # services
│   │   │   ├── mapper/                        # MyBatis mappers
│   │   │   ├── ws/                            # WebSocket server endpoint
│   │   │   ├── interceptor/                   # token interceptor
│   │   │   └── utils/                         # utils (JWT, image, etc.)
│   │   └── src/main/resources/
│   │       ├── application.yml                # config (env placeholders)
│   │       └── sql/init.sql                   # DB init script
│   └── dependencies/MinioOSSOperator/         # custom MinIO starter
└── frontend/vue-ezchat/
    ├── src/
    │   ├── api/                               # API wrappers
    │   ├── WS/                                # WebSocket client composable
    │   ├── stores/                            # Pinia stores
    │   ├── views/                             # pages
    │   └── i18n/locales/                      # zh/en/ja/ko/zh-tw
    └── vite.config.ts                         # dev proxy config
```

---

## Development Conventions

### Backend Conventions

#### Package Structure
- `hal.th50743.controller` - REST API endpoints
- `hal.th50743.service` - Business logic layer
- `hal.th50743.service.impl` - Service implementations
- `hal.th50743.mapper` - MyBatis data access layer
- `hal.th50743.pojo` - Data transfer objects and value objects
- `hal.th50743.exception` - Custom exceptions and error handling
- `hal.th50743.interceptor` - Request interceptors (auth, logging)
- `hal.th50743.ws` - WebSocket endpoint
- `hal.th50743.utils` - Utility classes (JWT, image, etc.)
- `hal.th50743.aspect` - AOP aspects

#### Database Conventions
- **No physical foreign keys** - Associations enforced at service layer
- **UTF-8MB4** character set and collation
- **Snake_case** for table and column names
- **Soft delete** pattern using `is_deleted` flag

#### API Response Format
All REST APIs return `Result<T>`:
```json
{
  "status": 1,  // 1=success, 0=failure
  "code": 200,   // HTTP-like status code
  "message": "success",
  "data": {}     // Generic payload
}
```

#### Error Code Ranges
- `40xxx`: Client errors (Bad request/Unauthorized/Forbidden/Not found)
- `41xxx`: User-related business errors
- `42xxx`: Chat/Message business errors
- `43xxx`: File business errors
- `50xxx`: System errors

#### Authentication
- **HTTP Header**: `token` (not `Authorization: Bearer ...`)
- **JWT Claim**: `uid` (lowercase) as user identifier
- **Exclusions**: `/auth/**` endpoints don't require authentication

---

### Frontend Conventions

#### TypeScript Conventions
- **Zero `any` Policy** - Use `unknown` with type guards or generics
- **No Lazy Assertions** - Avoid `as Type` assertions; prefer type guards
- **Type Aliases** - Use `type` over `interface` (except Vue component props/emits)
- **Centralized Types** - All global types in `src/type/index.ts`

#### Type Naming Patterns
- API Request Types: `{FunctionName}Req` (e.g., `LoginApiReq`, `GuestApiReq`)
- Response Types: `Result<T>` (generic wrapper)

#### API Function Pattern
```typescript
export type LoginApiReq = {
  username: string
  password: string
}

export const loginApi = (data: LoginApiReq): Promise<Result<LoginUser>> =>
  request.post('/auth/login', data)
```

#### Component Conventions
- **Vue 3 Composition API** with `<script setup>` syntax
- **TypeScript strict mode** enabled
- **Props/Emits** use `defineProps` and `defineEmits` with runtime types
- **Single File Components** (.vue)

#### State Management
- **Pinia stores** for global state
- Store naming: `use{Feature}Store` (e.g., `useUserStore`, `useChatStore`)

#### Internationalization
- Supported locales: `zh`, `en`, `ja`, `ko`, `zh-tw`
- Translation keys organized by feature
- System messages and error toasts fully localized

#### Styling
- **Dark mode** supported via Element Plus dark theme variables
- **Avatar border-radius**: Unified 30% ratio via `--avatar-border-radius-ratio` CSS variable
- **Scoped styles** in components

---

## Environment Configuration

### Environment Variables (Required)

| Variable | Description |
|----------|-------------|
| `DB_URL` | MySQL connection string |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | JWT signing key (min 256 bits) |
| `JWT_EXPIRATION` | JWT expiration time in ms |
| `OSS_ENDPOINT` | MinIO endpoint URL |
| `OSS_ACCESS_KEY` | MinIO access key |
| `OSS_SECRET_KEY` | MinIO secret key |
| `OSS_BUCKET_NAME` | MinIO bucket name |
| `OSS_PATH` | Path prefix for stored objects |

### Development Proxy (Vite)
- `/api/*` → `http://localhost:8080/*`
- `/websocket/*` → `ws://localhost:8080/websocket/*`

---

## API Endpoints

### Authentication
- `POST /auth/login` - Formal user login
- `POST /auth/register` - Register and login
- `POST /auth/register/upload` - Upload avatar for registration
- `POST /auth/guest` - Guest join via Room ID + Password
- `POST /auth/invite` - Guest join via invite code

### Initialization
- `GET /init/chat-list` - Lightweight init (chat list + user status)
- `GET /init` - Full initialization

### Chat Rooms
- `GET /chat/{chatCode}` - Get room details
- `GET /chat/{chatCode}/members` - Get members (lazy load)
- `POST /chat` - Create room

### Messages
- `GET /message?chatCode=...&cursorSeqId=...&limit=...` - Get messages
- `POST /message/upload` - Upload message image

### Media
- `GET /media/url?objectName=...` - Get fresh presigned URL
- `GET /media/check?rawHash=...` - Check if object exists (deduplication)

### User
- `GET /user/{uid}` - Get user profile
- `POST /user` - Update profile
- `PUT /user/password` - Change password
- `POST /user/upgrade` - Upgrade guest to formal

### WebSocket
- **Endpoint**: `/websocket/{token}`
- **Heartbeat**: Client sends `PING{chatCode}`, Server replies `PONG`
- **Message Codes**:
  - `1001` - `MESSAGE`
  - `2001` - `USER_STATUS`
  - `2002` - `ACK`
  - `3001` - `MEMBER_JOIN`

---

## Build & Run Commands

### Backend
```bash
cd backend/EZChat-parent
mvn spring-boot:run
```
Default port: **8080**

### Frontend
```bash
cd frontend/vue-ezchat
npm install
npm run dev      # Development server
npm run build    # Production build
npm run type-check  # TypeScript type checking
npm run lint     # ESLint with auto-fix
npm run format   # Prettier formatting
```
Dev server port: **5173**

---

## Key Features

- **Real-time messaging** via WebSocket with heartbeat and ACK
- **Dual token auth** (Access/Refresh) with auto-refresh
- **Guest and registered user** support
- **Auto-cleanup** of inactive guest accounts
- **Room creation** with password/invite/one-time link options
- **Message ordering** based on Sequence ID with pagination
- **Online/offline presence** with 30s debounce
- **Operation audit logging** for security
- **Image upload** with conditional thumbnails (10MB limit)
- **Default avatar generation** via DiceBear API
- **Image deduplication** with dual-hash strategy
- **Image optimization** (client compression + server normalization)
- **Refresh optimization** - load chat list first, lazy load members/messages
- **Full i18n** coverage (zh/en/ja/ko/zh-tw)
- **Dark mode** support

---

## Testing

### Database Init
```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ezchat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p ezchat < backend/EZChat-app/src/main/resources/sql/init.sql
```

**Warning**: `init.sql` drops tables, generates test data. Do NOT use in production.
