# EZChat / EZ Chat

一个基于 **Spring Boot 3 + Vue 3** 的现代化实时聊天系统：支持 WebSocket 实时消息、访客/注册登录、在线状态、图片上传与缩略图、国际化与暗黑模式。

A modern real-time chat system built with **Spring Boot 3 + Vue 3**: WebSocket messaging, guest/registered auth, presence, image upload + thumbnails, i18n, and dark mode.

---

## 功能特性 / Features

- **实时消息 / Real-time**：WebSocket 双向通信（消息广播、心跳、ACK）/ WebSocket messaging with heartbeat & ACK
- **认证 / Auth**：注册登录 + 访客加入（JWT）/ registered login + guest access (JWT)
- **聊天室 / Rooms**：通过 chatCode 获取房间信息并进入聊天 / join rooms via chatCode
- **在线状态 / Presence**：上线/离线广播 / online-offline presence broadcast
- **图片上传 / Image upload**：上传图片，按需生成缩略图（仅超阈值才生成）/ uploads with conditional thumbnails
- **图片优化 / Image optimization**：前端预压缩（提升上传体验）+ 后端规范化（兼容/隐私）/ client-side compression + server-side normalization
- **刷新体验优化 / Refresh UX**：refresh 时优先加载 chatList，成员/消息按需并行加载，减少黑屏与等待 / load chat list first on refresh; members & messages are lazy/parallel to reduce blank screen
- **国际化 / i18n**：`zh/en/ja/ko/zh-tw` / multi-language UI
- **暗黑模式 / Dark mode**：Element Plus 暗黑变量 / Element Plus dark theme vars

---

## 技术栈 / Tech Stack

### 后端 / Backend

- **Java**: 21
- **Spring Boot**: 3.3.4
- **WebSocket**: Jakarta WebSocket + `@ServerEndpoint`
- **MyBatis**: 3.0.3
- **MySQL**: 8.x
- **JWT**: `jjwt` 0.11.5
- **Object Storage**: MinIO（自研 starter：`minio-oss-spring-boot-starter`）
- **Thumbnail**: Thumbnailator 0.4.20

### 前端 / Frontend

- **Vue**: 3.5.25
- **TypeScript**: 5.9.x
- **Vite**: 7.2.x
- **Pinia**: 3.0.x
- **Vue Router**: 4.6.x
- **Element Plus**: 2.12.x
- **Axios**: 1.13.x

---

## 快速开始 / Quick Start

### 环境要求 / Prerequisites

- **Backend**：JDK 21+、Maven 3.6+、MySQL 8.x、MinIO（当前配置下为必需）  
  **Backend**: JDK 21+, Maven 3.6+, MySQL 8.x, MinIO (required by current config)
- **Frontend**：Node.js `^20.19.0 || >=22.12.0`、npm 10+  
  **Frontend**: Node.js `^20.19.0 || >=22.12.0`, npm 10+

### 数据库结构 / Database schema

本项目数据库初始化脚本位于 `backend/EZChat-app/src/main/resources/sql/init.sql`，当前结构使用 **MySQL 8** + **utf8mb4**。  
Schema is defined in `backend/EZChat-app/src/main/resources/sql/init.sql` (MySQL 8 + utf8mb4).

> ⚠️ 重要 / Important  
> `init.sql` 会 `DROP TABLE IF EXISTS` 并在存储过程中 `TRUNCATE` 多张表，然后生成大量测试数据（多语言昵称、房间、消息）。请勿用于生产库。  
> `init.sql` drops tables, truncates data inside stored procedure, and generates lots of test data. Do NOT use in production.

#### 表一览 / Tables

- `users`：所有用户（访客/正式用户共用）/ all users (guest + formal)
- `formal_users`：正式用户账号体系（用户名/密码哈希）/ credentials for formal users
- `chats`：聊天室（群/单聊统一存储）/ chat rooms
- `chat_members`：聊天室成员关系 + 最后阅读时间 / membership + last_seen_at
- `messages`：消息主体 / messages
- `files`：消息附带文件元数据（当前结构预留）/ file metadata (reserved)

#### 字段要点 / Key fields (summary)

`users`

| 字段 | 含义 (中文) | Meaning (EN) |
|---|---|---|
| `id` (PK) | 用户内部ID | internal user id |
| `uid` (UNIQUE, char(10)) | 用户对外ID（系统统一使用） | public user id (used across system) |
| `nickname` | 昵称 | nickname |
| `avatar_object` | MinIO 对象名（object name） | MinIO object name for avatar |
| `bio` | 简介 | bio |
| `last_seen_at` | 最后在线时间 | last seen at |
| `create_time` / `update_time` | 创建/更新时间 | created/updated timestamps |

`formal_users`

| 字段 | 含义 (中文) | Meaning (EN) |
|---|---|---|
| `user_id` (PK) | 对应 `users.id` | references `users.id` |
| `username` (UNIQUE) | 登录用户名 | login username |
| `password_hash` | 密码哈希（BCrypt） | password hash (BCrypt) |
| `token` (NULL) | 长期 Token（可选） | long-term token (optional) |
| `last_login_time` | 最后登录时间 | last login time |

`chats`

| 字段 | 含义 (中文) | Meaning (EN) |
|---|---|---|
| `id` (PK) | 聊天室内部ID | internal chat id |
| `chat_code` (UNIQUE, char(8)) | 聊天室对外ID | public chat code |
| `chat_name` | 聊天室名称 | chat name |
| `owner_id` | 群主 `users.id` | owner `users.id` |
| `chat_password_hash` | 进群密码哈希 | room password hash |
| `join_enabled` | 是否允许加入 | whether join is enabled |
| `avatar_name` | 聊天室头像对象名 | chat avatar object name |
| `create_time` / `update_time` | 创建/更新时间 | created/updated timestamps |

`chat_members`

| 字段 | 含义 (中文) | Meaning (EN) |
|---|---|---|
| `chat_id` (PK part) | 对应 `chats.id` | references `chats.id` |
| `user_id` (PK part) | 对应 `users.id` | references `users.id` |
| `last_seen_at` | 最后阅读该房间时间 | last seen time for this chat |

`messages`

| 字段 | 含义 (中文) | Meaning (EN) |
|---|---|---|
| `id` (PK) | 消息内部ID | internal message id |
| `sender_id` | 发送者 `users.id` | sender `users.id` |
| `chat_id` | 房间 `chats.id` | chat `chats.id` |
| `type` (tinyint) | 消息类型：0文本/1图片/2混合 | message type: 0 text / 1 image / 2 mixed |
| `text` | 文本内容（可空） | text content (nullable) |
| `object_names` | MinIO 对象名列表（JSON 字符串） | list of MinIO object names (JSON string) |
| `create_time` / `update_time` | 创建/更新时间 | created/updated timestamps |

`files`

| 字段 | 含义 (中文) | Meaning (EN) |
|---|---|---|
| `id` (PK) | 文件内部ID | internal file id |
| `message_id` | 对应 `messages.id` | references `messages.id` |
| `file_name` | 原始文件名 | original filename |
| `content_type` | MIME 类型 | MIME type |
| `file_path` | 存储路径（预留/历史） | storage path (reserved/legacy) |

#### 测试数据生成 / Test data generation

`init.sql` 会创建并执行存储过程 `generate_japanese_test_data()`，默认会：

`init.sql` creates and calls `generate_japanese_test_data()`, which by default:

- 插入约 **120** 个用户（多语言昵称）/ inserts ~**120** users (multilingual nicknames)
- 插入约 **120** 个正式用户凭证 / inserts ~**120** formal user credentials
- 创建约 **25** 个聊天室 / creates ~**25** chats
- 为每个房间生成 **100+** 条消息 / generates **100+** messages per chat
- 大量 `chat_members` 关系（含全员入群逻辑）/ many memberships (including full-join logic)

### 1) 初始化数据库 / Initialize database

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ezchat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p ezchat < backend/EZChat-app/src/main/resources/sql/init.sql
```

### 2) 配置环境变量 / Set environment variables

后端 `backend/EZChat-app/src/main/resources/application.yml` 全部使用 `${ENV}` 占位符，**必须**注入环境变量（本工程未集成 `.env` 自动加载）。  
Backend `application.yml` uses `${ENV}` placeholders; you **must** provide env vars (no built-in `.env` loader).

```bash
export DB_URL='jdbc:mysql://localhost:3306/ezchat?useSSL=false&serverTimezone=Asia/Tokyo'
export DB_USERNAME='root'
export DB_PASSWORD='your_password'

export JWT_SECRET='your_jwt_secret_key_at_least_256_bits'
export JWT_EXPIRATION='86400000'

export OSS_ENDPOINT='http://localhost:9000'
export OSS_ACCESS_KEY='minioadmin'
export OSS_SECRET_KEY='minioadmin'
export OSS_BUCKET_NAME='ezchat'
export OSS_PATH='images'
```

> 提示 / Tip  
> 使用 IntelliJ IDEA 启动后端时，请在 Run Configuration 中配置同名环境变量。  
> If you start backend in IntelliJ IDEA, set the same env vars in Run Configuration.

### 3) 启动后端 / Start backend

```bash
cd backend/EZChat-parent
mvn -q -pl ../EZChat-app spring-boot:run
```

默认端口 / Default port: **8080**

### 4) 启动前端 / Start frontend

```bash
cd frontend/vue-ezchat
npm install
npm run dev
```

访问 / Open: `http://localhost:5173`

---

## 当前工程约定 / Current Project Conventions

### 开发期代理 / Dev proxy (Vite)

前端开发期通过 `frontend/vue-ezchat/vite.config.ts` 做代理：  
Vite dev proxy rules:

- **`/api/*` → `http://localhost:8080/*`**（仅开发期前缀；后端实际没有 `/api` 前缀）  
  **`/api/*` → `http://localhost:8080/*`** (dev-only prefix; backend routes do NOT include `/api`)
- **`/websocket/*` → `ws://localhost:8080/websocket/*`**

### 鉴权 / Auth token

- **HTTP Header**：使用 `token`（不是 `Authorization: Bearer ...`）  
  **HTTP header key**: `token` (not `Authorization: Bearer ...`)
- **JWT Claim / 用户标识**：统一使用 `uid`（小写）  
  **JWT claim / user identifier**: `uid` (lowercase)
- **拦截规则 / Interceptor**：除 `/auth/**` 外均需要 token  
  Everything except `/auth/**` requires token

### WebSocket / WebSocket endpoint

- **Server endpoint**：`/websocket/{token}`
- **Close code**：
  - `4001`: Token Expired
  - `4002`: Authentication Failed

### 消息类型 / Message type

后端会为消息计算 `type` 字段（并在 DB 中持久化）：  
Backend computes and persists `type` for each message:

- `0`: Text（仅文本）/ only text
- `1`: Image（仅图片）/ only images
- `2`: Mixed（文本 + 图片）/ text + images

前端渲染规则：  
Frontend rendering rules:

- `type=0`：只渲染文字气泡 / render text bubble only
- `type=1`：只渲染图片列表（避免空文本气泡）/ render images only (no empty bubble)
- `type=2`：文字 + 图片都渲染 / render both

### 刷新初始化与加载策略 / Refresh initialization & loading strategy

refresh 场景下，前端采用“分阶段加载”以减少首屏阻塞：  
On refresh, frontend uses a staged initialization to reduce first-paint blocking:

- **阶段 1（阻塞）/ Stage 1 (blocking)**：先拉取 chatList + userStatusList，确保 AsideList 可用  
  Fetch chat list + user status first so the chat list renders ASAP.
- **阶段 2（并行/按需）/ Stage 2 (parallel/lazy)**：
  - **成员列表 / Members**：右侧成员栏进入房间后按需调用 `GET /chat/{chatCode}/members`，并异步预取成员头像缩略图  
    Right-side member list is lazy-loaded per room, then member avatar thumbs are prefetched async.
  - **消息列表 / Messages**：退出初始化后再拉取 `GET /message?chatCode=...&timeStamp=...`，消息图片缩略图异步预取  
    Messages load after initialization is released; message image thumbs are prefetched async.

Loading 视觉策略：  
Loading visuals:

- **全局遮蔽 / Global overlay**：由 `App.vue` 统一控制，全屏 Loading 结束时使用 View Transitions API 做淡出（不影响 root 过渡）  
  Controlled in `App.vue`; fade-out uses View Transitions API (root transitions disabled to avoid flicker).
- **局部遮蔽 / Local overlay**：右侧成员栏使用 `AppSpinner` 的 `absolute=true` 模式，磨砂玻璃（Glassmorphism）遮罩叠加在内容上  
  Right-side member area uses `AppSpinner(absolute=true)` as a glass overlay on top of existing content.

### 上传与图片处理链路 / Upload & image processing pipeline

当前工程采用“双层处理”策略：  
This project uses a two-stage pipeline:

- **前端预压缩 / Client-side compression**：
  - 依赖：`browser-image-compression`
  - 逻辑：上传前压缩并尽量转为 JPEG；压缩失败会自动回退原图上传
- **后端规范化 / Server-side normalization**：
  - 自动旋转（EXIF Orientation）
  - 去元数据/EXIF（通过重编码输出 JPEG）
  - 统一输出 JPEG（质量约 0.85，最大边 2048）
  - GIF 动图默认原样保留（避免丢失动效）

原图加载策略：  
Original image strategy:

- **原图 Blob 按需拉取 / On-demand original blob**：仅在打开预览时拉取原图 Blob；若本地 URL 失效，会调用 `GET /media/url?objectName=...` 获取最新 URL 再重试  
  Original blobs are fetched only when opening preview; if cached URL fails, frontend requests a fresh URL and retries.

后端上传/删除统一入口（便于未来扩展“文件功能”）：  
Unified backend entry for uploads/deletes (ready for future file features):

- `hal.th50743.service.OssMediaService`
- `hal.th50743.service.impl.OssMediaServiceImpl`
- 删除对象：`OssMediaService.deleteObject(objectNameOrUrl)`（会联动删除缩略图）

### 主要后端路由 / Main backend routes

> 说明 / Note：以下路径均是**后端真实路径**（不含 `/api` 前缀）。  
> These are backend paths (no `/api` prefix).

- `POST /auth/login`
- `POST /auth/register`
- `POST /auth/guest`
- `POST /auth/register/upload`
- `GET  /init`
- `GET  /init/chat-list`（轻量初始化：chatList + userStatusList）
- `GET  /chat/{chatCode}`
- `GET  /chat/{chatCode}/members`（成员列表懒加载）
- `GET  /message?chatCode=...&timeStamp=...`
- `POST /message/upload`
- `GET  /media/url?objectName=...`（获取最新图片访问 URL）
- `GET  /user/{uid}`
- `POST /user`

---

## API 接口文档 / API Reference

### 通用约定 / Common conventions

- **Base URL**：
  - **开发期 / Dev**：前端通过 Vite proxy 使用 `/api/*`（例如 `/api/init/chat-list`）  
    Frontend uses `/api/*` via Vite proxy in dev (e.g. `/api/init/chat-list`).
  - **后端真实路径 / Backend**：文档以下均为**不含 `/api`** 的真实后端路径  
    All endpoints below are backend real paths (**no `/api` prefix**).
- **鉴权 / Auth**：
  - 除 `/auth/**` 外，其他接口都需要请求头 `token: <jwt>`  
    All endpoints except `/auth/**` require header `token: <jwt>`.
- **响应格式 / Response**：
  - REST 接口统一返回 `Result`（`status=1` 成功，`status=0` 失败；成功 `code=200`）  
    All REST APIs return `Result` (`status=1` success, `status=0` failure; success uses `code=200`).

成功示例 / Success example:

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": {}
}
```

失败示例 / Failure example:

```json
{
  "status": 0,
  "code": 42001,
  "message": "Chat room not found",
  "data": null
}
```

---

### 认证 / Auth (`/auth/*`)

#### `POST /auth/login`（正式用户登录 / Formal login）

- **业务功能 / Purpose**：用户名密码登录，返回 JWT token（后续放在 `token` header）  
  Login with username/password and returns JWT token.
- **Auth**：无需 token / no token
- **Request (JSON)**：

```json
{ "username": "alice", "password": "P@ssw0rd!" }
```

- **Response (mock)**：

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": { "uid": "20000001", "username": "alice", "token": "eyJhbGciOi..." }
}
```

#### `POST /auth/register`（注册并登录 / Register + login）

- **业务功能 / Purpose**：创建正式用户并返回 token（前端一般先上传头像拿到 `avatar.objectName` 再提交注册）  
  Creates a formal user and returns token (frontend typically uploads avatar first to get `avatar.objectName`).
- **Auth**：无需 token / no token
- **Request (JSON)**（字段来自 `FormalUserRegisterReq`）:

```json
{
  "username": "alice",
  "password": "P@ssw0rd!",
  "confirmPassword": "P@ssw0rd!",
  "nickname": "Alice",
  "avatar": {
    "objectName": "public/avatars/20000001.jpg",
    "objectUrl": "http://localhost:9000/ezchat/public/avatars/20000001.jpg",
    "objectThumbUrl": "http://localhost:9000/ezchat/public/avatars/20000001_thumb.jpg"
  }
}
```

- **Response (mock)**：

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": { "uid": "20000001", "username": "alice", "token": "eyJhbGciOi..." }
}
```

#### `POST /auth/register/upload`（注册头像上传 / Upload avatar for register）

- **业务功能 / Purpose**：上传头像（后端会生成缩略图并返回 Image 信息）  
  Upload avatar (backend generates thumbnail and returns Image).
- **Auth**：无需 token / no token
- **Request**：`multipart/form-data`，字段名 `file`
- **Response (mock)**：

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": {
    "objectName": "public/avatars/20000001.jpg",
    "objectUrl": "http://localhost:9000/ezchat/public/avatars/20000001.jpg",
    "objectThumbUrl": "http://localhost:9000/ezchat/public/avatars/20000001_thumb.jpg"
  }
}
```

#### `POST /auth/guest`（访客加入房间 / Guest join）

- **业务功能 / Purpose**：用 chatCode + 昵称加入房间，返回访客 token  
  Join a room as guest and returns token.
- **Auth**：无需 token / no token
- **Request (JSON)**（字段来自 `GuestReq`）：

```json
{ "chatCode": "20000022", "password": "", "nickname": "Guest-01" }
```

- **Response (mock)**：

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": { "uid": "90000001", "username": "guest", "token": "eyJhbGciOi..." }
}
```

---

### 初始化 / Init (`/init/*`)

#### `GET /init/chat-list`（轻量初始化 / Lite init for chat list）

- **业务功能 / Purpose**：refresh 首屏优先拿到 chatList + userStatusList（不返回每个房间的成员列表）  
  Fetch chat list + user status for fast refresh (no per-room member list).
- **Auth**：需要 `token` / requires `token`
- **Request headers**：
  - `token: <jwt>`
- **Response (mock)**（字段来自 `AppInitVO` + `ChatVO`）：

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": {
    "chatList": [
      {
        "chatCode": "20000022",
        "chatName": "Study Group",
        "ownerUid": "20000001",
        "avatar": {
          "objectName": "public/chats/20000022.jpg",
          "objectUrl": "http://localhost:9000/ezchat/public/chats/20000022.jpg",
          "objectThumbUrl": "http://localhost:9000/ezchat/public/chats/20000022_thumb.jpg"
        },
        "unreadCount": 3,
        "onLineMemberCount": 5,
        "memberCount": 12,
        "lastActiveAt": "2026-01-02T10:20:30",
        "lastMessage": { "sender": "20000003", "chatCode": "20000022", "type": 0, "text": "hi", "images": null, "createTime": "2026-01-02T10:20:30" }
      }
    ],
    "userStatusList": [
      { "uid": "20000003", "online": true, "updateTime": "2026-01-02T10:20:00" }
    ]
  }
}
```

#### `GET /init`（完整初始化 / Full init）

- **业务功能 / Purpose**：返回更完整的初始化数据（可能包含更多房间聚合字段）  
  Full init (may include more aggregated room data).
- **Auth**：需要 `token` / requires `token`

---

### 聊天室 / Chat (`/chat/*`)

#### `GET /chat/{chatCode}`（获取房间详情 / Get room detail）

- **业务功能 / Purpose**：进入房间时获取完整 `ChatVO`（包含成员、未读数、最后消息等）  
  Fetch full room detail `ChatVO` (members, unread, last message, etc.).
- **Auth**：需要 `token` / requires `token`
- **Path**：`chatCode`（对外房间号 / public room code）
- **Response (mock)**：

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": {
    "chatCode": "20000022",
    "chatName": "Study Group",
    "memberCount": 12,
    "onLineMemberCount": 5,
    "chatMembers": [
      {
        "uid": "20000003",
        "nickname": "Bob",
        "online": true,
        "lastSeenAt": "2026-01-02T10:10:00",
        "avatar": {
          "objectName": "public/avatars/20000003.jpg",
          "objectUrl": "http://localhost:9000/ezchat/public/avatars/20000003.jpg",
          "objectThumbUrl": "http://localhost:9000/ezchat/public/avatars/20000003_thumb.jpg"
        }
      }
    ]
  }
}
```

#### `GET /chat/{chatCode}/members`（成员列表懒加载 / Lazy members）

- **业务功能 / Purpose**：右侧成员栏按需获取成员列表，避免 refresh 阶段全量加载所有房间成员  
  Lazy-load members for right sidebar to avoid heavy init.
- **Auth**：需要 `token` / requires `token`
- **Response (mock)**：`ChatMemberVO[]`

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": [
    {
      "uid": "20000003",
      "nickname": "Bob",
      "online": true,
      "lastSeenAt": "2026-01-02T10:10:00",
      "avatar": {
        "objectName": "public/avatars/20000003.jpg",
        "objectUrl": "http://localhost:9000/ezchat/public/avatars/20000003.jpg",
        "objectThumbUrl": "http://localhost:9000/ezchat/public/avatars/20000003_thumb.jpg"
      }
    }
  ]
}
```

#### `POST /chat`（创建房间 / Create room）

- **业务功能 / Purpose**：创建聊天室（当前实现为占位，后续可补齐）  
  Create chat room (currently stub).
- **Auth**：需要 `token` / requires `token`
- **Response**：`Result.success()`（无 data / no data）

---

### 消息 / Message (`/message/*`)

#### `GET /message?chatCode=...&timeStamp=...`（拉取消息列表 / Get messages）

- **业务功能 / Purpose**：
  - 拉取指定房间消息列表（可分页：传入 `timeStamp` 获取更早历史）  
    Fetch messages for a chat (pagination via `timeStamp`).
  - 服务端会同步更新当前用户在该房间的 `last_seen_at`（已读游标）  
    Server updates membership `last_seen_at`.
- **Auth**：需要 `token` / requires `token`
- **Query**：
  - `chatCode`：必填
  - `timeStamp`：可选，格式为 `LocalDateTime` 字符串，例如 `2026-01-02T10:00:00`
- **Response (mock)**：`data` 为对象，包含 `messageList` 与 `chatRoom`

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": {
    "messageList": [
      {
        "sender": "20000003",
        "chatCode": "20000022",
        "type": 2,
        "text": "hello",
        "images": [
          {
            "objectName": "private/messages/20000022/abc.jpg",
            "objectUrl": "https://minio.example.com/presigned/original...",
            "objectThumbUrl": "https://minio.example.com/presigned/thumb..."
          }
        ],
        "createTime": "2026-01-02T10:20:30"
      }
    ],
    "chatRoom": { "chatCode": "20000022", "chatName": "Study Group" }
  }
}
```

#### `POST /message/upload`（上传消息图片 / Upload message image）

- **业务功能 / Purpose**：上传聊天图片，返回 `Image`（含缩略图 URL）  
  Upload chat image and returns `Image` (with thumbnail URL).
- **Auth**：需要 `token` / requires `token`
- **Request**：`multipart/form-data`，字段名 `file`
- **Response (mock)**：

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": {
    "objectName": "private/messages/20000022/abc.jpg",
    "objectUrl": "https://minio.example.com/presigned/original...",
    "objectThumbUrl": "https://minio.example.com/presigned/thumb..."
  }
}
```

---

### 媒体 / Media (`/media/*`)

#### `GET /media/url?objectName=...`（刷新图片 URL / Refresh image URL）

- **业务功能 / Purpose**：前端在预览原图时按需获取最新预签名 URL（避免过期）  
  Fetch a fresh presigned URL for preview to avoid expiration.
- **Auth**：需要 `token` / requires `token`
- **Query**：
  - `objectName`：必填（建议传原图 objectName）
- **Response (mock)**：`data` 为 URL 字符串

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": "https://minio.example.com/presigned/original?X-Amz-Algorithm=..."
}
```

---

### 用户 / User (`/user/*`)

#### `GET /user/{uid}`（获取用户信息 / Get user profile）

- **业务功能 / Purpose**：获取用户公开信息（含权限校验：本人或共同群成员）  
  Get user profile (authorized: self or same-room member).
- **Auth**：需要 `token` / requires `token`
- **Response (mock)**：`UserVO`

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": {
    "uid": "20000003",
    "nickname": "Bob",
    "bio": "Hello",
    "avatar": {
      "objectName": "public/avatars/20000003.jpg",
      "objectUrl": "http://localhost:9000/ezchat/public/avatars/20000003.jpg",
      "objectThumbUrl": "http://localhost:9000/ezchat/public/avatars/20000003_thumb.jpg"
    }
  }
}
```

#### `POST /user`（更新用户信息 / Update profile）

- **业务功能 / Purpose**：更新昵称/头像/简介（用户 ID 由后端从 token 注入）  
  Update nickname/avatar/bio (user id injected from token).
- **Auth**：需要 `token` / requires `token`
- **Request (JSON)**（字段来自 `UserReq`）：

```json
{
  "uid": "20000003",
  "nickname": "Bob (Updated)",
  "bio": "New bio",
  "avatar": { "objectName": "public/avatars/20000003.jpg" }
}
```

- **Response**：当前实现返回 **HTTP 200 + 空响应体**（建议后续改为 `Result.success()` 以保持一致性）  
  Current implementation returns **HTTP 200 with empty body** (recommended to return `Result.success()` for consistency).

---

### WebSocket 协议 / WebSocket protocol

- **Endpoint**：`/websocket/{token}`（示例：`ws://localhost:8080/websocket/<token>`）
- **心跳 / Heartbeat**：
  - Client → `PING{chatCode}`（例如 `PING20000022`）
  - Server → `PONG`
- **消息封装 / Envelope**：服务端推送消息统一为：

```json
{ "isSystemMessage": 0, "type": "MESSAGE", "data": { } }
```

- **常见 type / Common types**：
  - `MESSAGE`：聊天消息（data 为 `MessageVO`）
  - `ACK`：发送确认（data 为 `tempId`）
  - `USER_STATUS`：在线状态广播（data 为 `UserStatus`）

- **客户端发送消息 / Client → Server (mock)**（字段来自 `MessageReq`）：

```json
{
  "sender": "20000003",
  "chatCode": "20000022",
  "text": "hello",
  "images": [
    { "objectUrl": "https://minio.example.com/presigned/original..." }
  ],
  "tempId": "kq3f2v7x"
}
```

- **服务端广播消息 / Server → Clients (mock)**：

```json
{
  "isSystemMessage": 0,
  "type": "MESSAGE",
  "data": {
    "sender": "20000003",
    "chatCode": "20000022",
    "type": 2,
    "text": "hello",
    "images": [
      {
        "objectName": "private/messages/20000022/abc.jpg",
        "objectUrl": "https://minio.example.com/presigned/original...",
        "objectThumbUrl": "https://minio.example.com/presigned/thumb..."
      }
    ],
    "createTime": "2026-01-02T10:20:30"
  }
}
```

---

### 统一响应与异常处理 / Unified response & exception handling

后端所有 REST 接口统一返回 `hal.th50743.pojo.Result`：  
All REST APIs return `hal.th50743.pojo.Result`:

- **字段 / Fields**
  - `status`: `1`=success, `0`=failure
  - `code`: 业务码 / business error code（成功固定为 `200`）
  - `message`: 说明信息 / message
  - `data`: 业务数据 / payload

最小示例 / Minimal examples:

```json
{ "status": 1, "code": 200, "message": "success", "data": { } }
```

```json
{ "status": 0, "code": 42001, "message": "Chat room not found", "data": null }
```

- **ErrorCode 分段 / ErrorCode ranges**（见 `hal.th50743.exception.ErrorCode`）
  - `40xxx`: Client errors（Bad request/Unauthorized/Forbidden/Not found）
  - `41xxx`: User 相关业务错误 / user business errors
  - `42xxx`: Chat/Message 相关业务错误 / chat & message business errors
  - `43xxx`: File 相关业务错误 / file business errors
  - `50xxx`: Server errors / system errors

- **全局异常处理 / GlobalExceptionHandler**（`hal.th50743.exception.GlobalExceptionHandler`）
  - **BusinessException** → `Result.error(code, message)`
  - **DuplicateKeyException** → `Result.error(DATABASE_ERROR, "... is already exist")`（会尝试从异常信息解析字段）
  - **DataIntegrityViolationException** → 若包含 `Data truncation`，返回 `BAD_REQUEST` + `"Input value too long"`，否则 `DATABASE_ERROR`
  - **Exception (fallback)** → `Result.error(SYSTEM_ERROR)`

- **HTTP 状态码约定 / HTTP status convention**
  - **大多数业务错误**：仍返回 HTTP `200`，通过 `status=0` + `code` 表达失败。  
    Most business failures still return HTTP `200` and use `status=0` + `code`.
  - **鉴权失败**：`TokenInterceptor` 会直接返回 HTTP `401`（并中断请求）。  
    Auth failures are returned as HTTP `401` by `TokenInterceptor`.

---

## 项目结构 / Project Structure

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

## 常见问题 / Troubleshooting

### 1) 前端请求后端 404 / Frontend gets 404 from backend

- **原因 / Cause**：开发期必须通过 `/api` 前缀走 Vite proxy；后端没有 `/api` 前缀。  
  Dev uses `/api` (Vite proxy). Backend routes do not have `/api`.
- **检查 / Check**：`frontend/vue-ezchat/vite.config.ts` 的 proxy 是否指向 `8080`。

### 2) 后端启动失败：占位符未解析 / Backend fails: unresolved placeholders

- **原因 / Cause**：未设置 `DB_*` / `JWT_*` / `OSS_*` 环境变量（本工程不自动加载 `.env`）。  
  Missing `DB_*` / `JWT_*` / `OSS_*` env vars (no `.env` loader).
- **Fix**：按“配置环境变量 / Set environment variables”导出环境变量或配置 IDE 运行变量。

### 3) WebSocket 断开（4001/4002）/ WebSocket closed with 4001/4002

- **4001**：Token 过期 → 重新登录 / re-login
- **4002**：认证失败 → 检查 URL 是否为 `/websocket/{token}` / validate token in URL

### 4) HTTP 401 / Unauthorized

- **原因 / Cause**：缺少请求头 `token` 或 token 无效。  
  Missing/invalid `token` header.


