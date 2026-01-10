# EZChat / EZ Chat

一个基于 **Spring Boot 3 + Vue 3** 的现代化实时聊天系统：支持 WebSocket 实时消息、访客/注册登录、在线状态、图片上传与缩略图、国际化与暗黑模式。

A modern real-time chat system built with **Spring Boot 3 + Vue 3**: WebSocket messaging, guest/registered auth, presence, image upload + thumbnails, i18n, and dark mode.

---

## 功能特性 / Features

- **实时消息 / Real-time**：WebSocket 双向通信（消息广播、心跳、ACK）/ WebSocket messaging with heartbeat & ACK
- **认证 / Auth**：双 Token 机制（Access/Refresh）+ 自动刷新，支持注册登录与访客加入 / Dual Token (Access/Refresh) + auto-refresh, supports registered/guest access
- **自动清理 / Auto-Cleanup**：自动清理长期离线的访客账号及其数据 / Auto-cleanup of inactive guest accounts
- **聊天室 / Rooms**：创建房间（可使用密码加入/邀请链接/一次性链接）、通过 chatCode 获取房间信息并进入聊天 / create rooms (join with password/invite links/one-time links), join rooms via chatCode
- **用户设置 / User Settings**：更新个人资料（头像/昵称/Bio）、修改密码（仅正式用户） / Update profile (avatar/nickname/bio), change password (formal users only)
- **消息排序 / Message Ordering**：基于 Sequence ID 的精确消息排序与分页 / Precise message ordering & pagination based on Sequence ID
- **在线状态 / Presence**：上线/离线广播 / online-offline presence broadcast
- **状态防抖 / Presence Debounce**：30s 离线缓冲，防止网络波动造成误报 / 30s offline buffer to prevent status flickering
- **操作审计 / Audit Logging**：全量操作日志记录，支持安全审计 / Comprehensive operation logging for security audit
- **图片上传 / Image upload**：上传图片，按需生成缩略图（仅超阈值才生成），统一大小限制 10MB / uploads with conditional thumbnails, unified 10MB size limit
- **默认头像生成 / Default avatar generation**：使用 DiceBear API 自动生成默认头像（用户使用 bottts-neutral，房间使用 identicon），未上传头像时自动使用 / auto-generates default avatars via DiceBear API (bottts-neutral for users, identicon for rooms) when no avatar uploaded
- **图片去重 / Image deduplication**：双哈希策略（前端预计算 + 后端规范化哈希）防止重复上传，节省存储空间 / dual-hash strategy (frontend pre-calculation + backend normalized hash) prevents duplicate uploads
- **图片优化 / Image optimization**：前端预压缩（提升上传体验）+ 后端规范化（兼容/隐私）/ client-side compression + server-side normalization
- **刷新体验优化 / Refresh UX**：refresh 时优先加载 chatList，成员/消息按需并行加载，减少黑屏与等待 / load chat list first on refresh; members & messages are lazy/parallel to reduce blank screen
- **国际化 / i18n**：`zh/en/ja/ko/zh-tw` 全面覆盖（含系统消息与错误提示） / Full coverage (incl. system messages & error toasts)
- **类型安全 / Type Safety**：Zod 运行时校验 + TypeScript 严格模式 / Runtime validation (Zod) + TS strict mode
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
- `chat_invites`：聊天室邀请码（短链接加入权限，含 TTL / 次数 / 撤销）/ invite codes (short link join permission, TTL/usage/revoke)
- `messages`：消息主体（含 seq_id） / messages (with seq_id)
- `objects`：对象存储元数据（图片/文件对象，含去重哈希字段）/ object storage metadata (images/files with deduplication hash fields)
- `operation_logs`：操作审计日志 / operation audit logs

#### 字段要点 / Key fields (summary)

`users`

| 字段 | 含义 (中文) | Meaning (EN) |
|---|---|---|
| `id` (PK) | 用户内部ID | internal user id |
| `uid` (UNIQUE, char(10)) | 用户对外ID（系统统一使用） | public user id (used across system) |
| `nickname` | 昵称 | nickname |
| `object_id` | 关联 `objects.id`（头像对象，逻辑外键） | references `objects.id` (avatar object, logical FK) |
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
| `chat_password_hash` | 进群密码哈希（NULL=禁用密码加入功能，非NULL=启用密码加入） | room password hash (NULL=password join disabled, non-NULL=password join enabled) |
| `join_enabled` | 全局加入开关（0=禁止所有方式，1=允许加入） | global join switch (0=all disabled, 1=enabled) |
| `object_id` | 关联 `objects.id`（头像对象，逻辑外键） | references `objects.id` (avatar object, logical FK) |
| `create_time` / `update_time` | 创建/更新时间 | created/updated timestamps |

`chat_members`

| 字段 | 含义 (中文) | Meaning (EN) |
|---|---|---|
| `chat_id` (PK part) | 对应 `chats.id` | references `chats.id` |
| `user_id` (PK part) | 对应 `users.id` | references `users.id` |
| `last_seen_at` | 最后阅读该房间时间 | last seen time for this chat |

`chat_invites`

> 说明 / Note  
> 本工程**不使用物理外键（FK）**；`chat_invites.chat_code` 与 `chats.chat_code` 的关联由 Service 层校验保证。  
> This project **does not use physical foreign keys**; association is enforced by service-layer checks.

| 字段 | 含义 (中文) | Meaning (EN) |
|---|---|---|
| `id` (PK) | 邀请码记录ID | invite record id |
| `chat_code` (index) | 房间对外ID | public chat code |
| `code_hash` (UNIQUE) | 邀请短码 SHA-256 Hex（服务端只存哈希） | SHA-256 hex of invite code (server stores hash only) |
| `expires_at` | 过期时间（TTL） | expiration time (TTL) |
| `max_uses` | 最大使用次数：0=无限，1=一次性链接（使用一次即失效） | max uses: 0=unlimited, 1=one-time (invalid after first use) |
| `used_count` | 已使用次数 | used count |
| `revoked` | 是否撤销：0有效/1撤销 | revoked flag |
| `created_by` | 创建者 users.id（仅审计，无 FK） | created by users.id (audit only, no FK) |
| `create_time` / `update_time` | 创建/更新时间 | created/updated timestamps |

`messages`

| 字段 | 含义 (中文) | Meaning (EN) |
|---|---|---|
| `id` (PK) | 消息内部ID | internal message id |
| `sender_id` | 发送者 `users.id` | sender `users.id` |
| `chat_id` | 房间 `chats.id` | chat `chats.id` |
| `seq_id` (Index) | 消息序列号（单调递增，用于排序和分页） | message sequence id (monotonic, for ordering & paging) |
| `type` (tinyint) | 消息类型：0文本/1图片/2混合 | message type: 0 text / 1 image / 2 mixed |
| `text` | 文本内容（可空） | text content (nullable) |
| `object_ids` | 图片对象ID列表（JSON数组格式，如 `[1,2,3]`，存储 `objects.id`） | list of image object IDs (JSON array, e.g. `[1,2,3]`, stores `objects.id`) |
| `create_time` / `update_time` | 创建/更新时间 | created/updated timestamps |

`objects`

| 字段 | 含义 (中文) | Meaning (EN) |
|---|---|---|
| `id` (PK) | 对象内部ID | internal object id |
| `object_name` (UNIQUE, index) | MinIO 对象名（完整路径） | MinIO object name (full path) |
| `category` | 对象分类（USER_AVATAR/CHAT_COVER/MESSAGE_IMG/GENERAL） | object category |
| `status` | 状态（0=PENDING 待激活，1=ACTIVE 已激活） | status (0=PENDING, 1=ACTIVE) |
| `raw_object_hash` (index) | 原始对象 SHA-256 哈希（前端计算，用于快速去重检查） | raw object SHA-256 hash (frontend-calculated, for quick dedup check) |
| `normalized_object_hash` (index) | 规范化后对象 SHA-256 哈希（后端计算，用于最终去重） | normalized object SHA-256 hash (backend-calculated, for final dedup) |
| `create_time` / `update_time` | 创建/更新时间 | created/updated timestamps |

#### 测试数据生成 / Test data generation

`init.sql` 会创建并执行存储过程 `generate_test_data()`，默认会：

`init.sql` creates and calls `generate_test_data()`, which by default:

- 插入 **30 条种子数据**（真实头像，ID 1-30）到 `objects` 表 / inserts **30 seed objects** (real avatars, ID 1-30) into `objects` table
- 插入 **100** 个用户（每个用户随机选择一个种子头像）/ inserts **100** users (each randomly selects a seed avatar)
- 插入 **100** 个正式用户凭证 / inserts **100** formal user credentials
- 创建 **20** 个聊天室（每个聊天室克隆一个种子数据作为封面）/ creates **20** chats (each clones a seed object as cover)
- 为每个房间生成 **50** 条消息（20% 概率是图片消息，图片从种子数据中随机选择）/ generates **50** messages per chat (20% chance of image messages, images randomly selected from seeds)
- **所有用户加入所有聊天室**（全员入全群）/ all users join all chats (full membership)
- 生成 **10 条垃圾数据**（PENDING 状态，48 小时前创建，用于测试 GC）/ generates **10 garbage objects** (PENDING status, created 48h ago, for GC testing)

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

### 代码规范 / Code Conventions

#### TypeScript 类型定义 / TypeScript Type Definitions

- **类型别名优先 / Prefer `type` aliases**：所有类型定义使用 `type` 而非 `interface`（Vue 组件 Props/Emits 除外）  
  All type definitions use `type` aliases instead of `interface` (except Vue component props/emits)
- **集中管理 / Centralized**：所有全局类型定义位于 `frontend/vue-ezchat/src/type/index.ts`  
  All global types are defined in `frontend/vue-ezchat/src/type/index.ts`
- **类型命名 / Naming**：
  - API 请求类型：`{FunctionName}Req`（如 `LoginApiReq`, `GuestApiReq`）  
    API request types: `{FunctionName}Req` (e.g., `LoginApiReq`, `GuestApiReq`)
  - 响应类型：`Result<T>`（泛型包装器）  
    Response types: `Result<T>` (generic wrapper)

#### 类型安全协议 / Type Safety Protocol

- **Zero `any` Policy**：严格禁止使用 `any` 类型，使用 `unknown` 配合类型守卫或泛型代替  
  Explicit `any` is strictly prohibited; use `unknown` with type guards or generics instead
- **No Lazy Assertions**：避免使用 `as Type` 类型断言，优先使用类型守卫（Type Guards）或控制流类型收窄  
  Avoid `as Type` assertions; prefer type guards or control flow narrowing
- **运行时验证 / Runtime Validation**：API 响应数据使用类型守卫进行运行时验证  
  API response data should be validated at runtime using type guards
- **可接受的异常 / Acceptable Exceptions**：
  - DOM API 类型断言（如 `document.getElementById(...) as HTMLInputElement`）  
    DOM API type assertions (e.g., `document.getElementById(...) as HTMLInputElement`)
  - 实验性浏览器 API（如 View Transition API）  
    Experimental browser APIs (e.g., View Transition API)
  - 第三方库类型不匹配（需要添加注释说明原因）  
    Third-party library type mismatches (must be documented with comments)

#### API 函数模式 / API Function Patterns

- **参数对象模式 / Parameter Object Pattern**：所有 API 函数接受单个参数对象，而非多个原始参数  
  All API functions accept a single parameter object instead of multiple primitive parameters
- **类型定义 / Type Definitions**：
  - 请求类型：`{FunctionName}Req`（如 `LoginApiReq: { username: string, password: string }`）  
    Request types: `{FunctionName}Req` (e.g., `LoginApiReq: { username: string, password: string }`)
  - 响应类型：`Promise<Result<T>>`  
    Response types: `Promise<Result<T>>`
- **示例 / Example**：

```typescript
export type LoginApiReq = {
  username: string
  password: string
}

export const loginApi = (data: LoginApiReq): Promise<Result<LoginUser>> =>
  request.post('/auth/login', data)
```

#### 后端类型安全 / Backend Type Safety

- **泛型 Result 类 / Generic Result Class**：后端 `Result<T>` 类完全泛型化，所有 Controller 方法使用正确的泛型类型  
  Backend `Result<T>` class is fully genericized; all controller methods use proper generic types
- **错误响应 / Error Responses**：错误响应使用 `Result<?>` 通配符类型  
  Error responses use `Result<?>` wildcard type
- **类型一致性 / Type Consistency**：前后端 `Result<T>` 结构保持一致  
  Frontend and backend `Result<T>` structures are consistent

---

### 刷新初始化与加载策略 / Refresh initialization & loading strategy

refresh 场景下，前端采用"分阶段加载"以减少首屏阻塞：  
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

当前工程采用"双层处理"策略：  
This project uses a two-stage pipeline:

- **前端预压缩 / Client-side compression**：
  - 依赖：`browser-image-compression`
  - 逻辑：上传前压缩并尽量转为 JPEG；压缩失败会自动回退原图上传
  - **大小限制**：统一限制为 **10MB**（单张图片），超过限制会提示错误
- **后端规范化 / Server-side normalization**：
  - GIF 文件：**完全跳过规范化处理**，直接上传原始文件（避免丢失动效）
  - 其他图片：
  - 自动旋转（EXIF Orientation）
  - 去元数据/EXIF（通过重编码输出 JPEG）
  - 统一输出 JPEG（质量约 0.85，最大边 2048）

### 图片去重机制 / Image deduplication mechanism

系统采用**双哈希策略**防止重复上传相同图片：  
The system uses a **dual-hash strategy** to prevent duplicate uploads:

- **前端预计算 / Frontend pre-calculation**：
  - 使用 Web Crypto API 计算原始文件的 SHA-256 哈希（`raw_object_hash`）
  - 上传前调用 `GET /media/check?rawHash=...` 检查是否已存在
  - 如果已存在，直接复用现有对象（返回 `objectId`），跳过上传
- **后端规范化哈希 / Backend normalized hash**：
  - 后端对规范化后的图片（旋转、去 EXIF、压缩后）计算 SHA-256 哈希（`normalized_object_hash`）
  - 用于最终去重判断：即使原始文件不同，规范化后内容相同也会被识别为重复
  - 确保存储中不会出现内容相同但格式不同的重复对象

原图加载策略：  
Original image strategy:

- **原图 Blob 按需拉取 / On-demand original blob**：仅在打开预览时拉取原图 Blob；若本地 URL 失效，会调用 `GET /media/url?objectName=...` 获取最新 URL 再重试  
  Original blobs are fetched only when opening preview; if cached URL fails, frontend requests a fresh URL and retries.

后端上传/删除统一入口（便于未来扩展"文件功能"）：  
Unified backend entry for uploads/deletes (ready for future file features):

- `hal.th50743.service.MediaService`
- `hal.th50743.service.impl.MediaServiceImpl`
- 删除对象：`OssMediaService.deleteObject(objectNameOrUrl)`（会联动删除缩略图）

### 默认头像与样式统一 / Default Avatar & Style Unification

**默认头像生成**：
- 使用 DiceBear API 自动生成默认头像，支持两种类型：
  - **用户头像**：`bottts-neutral` 风格（机器人风格，适合用户）
  - **房间头像**：`identicon` 风格（几何图形，适合房间）
- 组件加载时自动生成默认头像 URL 用于展示
- 表单提交时，如果未上传头像，自动上传默认头像到服务器

**头像样式统一**：
- 所有头像使用统一的圆角比例（30%），通过全局 CSS 变量 `--avatar-border-radius-ratio` 统一管理
- 头像上传器（注册、创建房间、访客加入）统一为方形圆角样式
- 头像展示组件（SmartAvatar）使用按比例计算的圆角，确保视觉一致性

### 对象关联设计 / Object association design

系统统一使用**逻辑外键**关联 `objects` 表，避免通过 `objectName` 查询，提升性能：  
The system uses **logical foreign keys** to reference `objects` table, avoiding `objectName` lookups for better performance:

- **`users.object_id`**：关联用户头像对象（`objects.id`）
- **`chats.object_id`**：关联聊天室头像对象（`objects.id`）
- **`messages.object_ids`**：关联消息图片对象列表（JSON 数组格式，如 `[1,2,3]`，存储 `objects.id` 列表）
- **上传接口返回 `objectId`**：所有图片上传接口（`POST /auth/register/upload`、`POST /message/upload`、`GET /media/check`）都会返回 `Image` 对象，包含 `objectId` 字段，前端可直接使用，无需后端查表

### 主要后端路由 / Main backend routes

> 说明 / Note：以下路径均是**后端真实路径**（不含 `/api` 前缀）。  
> These are backend paths (no `/api` prefix).

- `POST /auth/login`
- `POST /auth/register`
- `POST /auth/guest`
- `POST /auth/invite`（邀请码免密加入，返回 token）
- `POST /auth/register/upload`
- `GET  /init`
- `GET  /init/chat-list`（轻量初始化：chatList + userStatusList）
- `GET  /chat/{chatCode}`
- `GET  /chat/{chatCode}/members`（成员列表懒加载）
- `GET  /message?chatCode=...&timeStamp=...`
- `POST /message/upload`
- `GET  /media/url?objectName=...`（获取最新图片访问 URL）
- `GET  /media/check?rawHash=...`（检查对象是否已存在，用于前端去重）
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
  "data": { 
    "uid": "20000001", 
    "username": "alice", 
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJ..." 
  }
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
    "objectThumbUrl": "http://localhost:9000/ezchat/public/avatars/20000001_thumb.jpg",
    "objectId": 123
  }
}
```

- **Response (mock)**：

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": { 
    "uid": "20000001", 
    "username": "alice", 
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJ..." 
  }
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
    "objectThumbUrl": "http://localhost:9000/ezchat/public/avatars/20000001_thumb.jpg",
    "objectId": 123
  }
}
```

#### `POST /auth/guest`（访客加入房间 / Guest join）

- **业务功能 / Purpose**：用 Room ID (chatCode) + 密码（可选）+ 昵称加入房间，返回访客 token  
  Join a room as guest via Room ID (chatCode) + password (optional) + nickname, returns token.
- **Auth**：无需 token / no token
- **核心规则 / Rules**：
  - **密码验证**：
    - 如果房间启用了密码加入功能（`chat_password_hash != NULL`），则必须提供正确的密码
    - 如果房间未启用密码加入功能（`chat_password_hash = NULL`），则**不能通过此接口加入**（只能通过邀请链接加入）
  - **强制 join_enabled**：当房间 `join_enabled=0` 时，此接口不可加入（全局禁止加入）
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
  "data": { 
    "uid": "90000001", 
    "username": "guest", 
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJ..." 
  }
}
```

#### `POST /auth/invite`（邀请码免密加入 / Invite join as guest）

- **业务功能 / Purpose**：
  - 使用短邀请码 `inviteCode` 免密加入指定 `chatCode`，并创建临时用户返回 JWT  
    Join via short invite code (passwordless) and returns JWT for the created guest user.
- **Auth**：无需 token / no token
- **核心规则 / Rules**：
  - **免密 / passwordless**：即使房间启用了密码加入功能（`chat_password_hash != NULL`），也不需要输入密码  
  - **强制 join_enabled**：当房间 `join_enabled=0` 时，邀请码也不可加入（全局禁止加入）  
  - **TTL**：默认 7 天（前端默认 `joinLinkExpiryMinutes=10080`，后端兜底同值）
  - **最多 5 条同时生效**：同一房间最多可以有 5 条有效邀请链接同时存在
- **Request (JSON)**（字段来自 `InviteGuestReq`）：

```json
{ "chatCode": "20000022", "inviteCode": "Ab3X9kQpQW1eR2tZ", "nickname": "Guest-Invite" }
```

- **Response (mock)**：

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": { "uid": "90000002", "username": "Guest-Invite", "token": "eyJhbGciOi..." }
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
          "objectThumbUrl": "http://localhost:9000/ezchat/public/chats/20000022_thumb.jpg",
          "objectId": 456
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
          "objectThumbUrl": "http://localhost:9000/ezchat/public/avatars/20000003_thumb.jpg",
          "objectId": 123
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
        "objectThumbUrl": "http://localhost:9000/ezchat/public/avatars/20000003_thumb.jpg",
        "objectId": 123
      }
    }
  ]
}
```

#### `POST /chat`（创建房间 / Create room）

- **业务功能 / Purpose**：创建聊天室并返回 `chatCode + inviteCode`（短邀请码，用于生成邀请链接）  
  Create chat room and returns `chatCode + inviteCode` (short code for invite link).
- **Auth**：需要 `token` / requires `token`
- **核心规则 / Rules**：

  **1. 密码加入功能开关（Join with Password）**  
  由 `chats.chat_password_hash` 是否为 `NULL` 决定（前端 UI 显示为"可使用密码加入"）：
  - **开启（`chat_password_hash != NULL`）**：
    - ✅ 可通过 **Room ID + Password** 加入
    - ✅ 可通过 **Invite Link** 加入（绕过密码验证）
  - **关闭（`chat_password_hash = NULL`）**：
    - ❌ **不能通过 Room ID 加入**（即使没有密码也不行）
    - ✅ **只能通过 Invite Link 加入**
  
  **2. 全局加入开关（Global Join Switch）**  
  由 `chats.join_enabled` 控制（创建时固定为 `1`，前端 UI 暂不提供控制）：
  - **`join_enabled = 1`**：允许加入（具体方式由密码加入功能开关决定）
  - **`join_enabled = 0`**：**禁止所有加入方式**（包括 Room ID + Password 和 Invite Link）
  
  **3. 邀请链接设置（Invite Link Settings）**  
  - **发行时必须设定有效期**：每次发行邀请链接时，必须设置 `expires_at`（由 `joinLinkExpiryMinutes` 控制，前端默认 10080 分钟 = 7 天）
  - **最多同时生效 5 条**：同一房间最多可以有 5 条有效邀请链接同时存在
  - **可再次发行和管理**：房间创建后，可在 Room Settings 中再次发行新的邀请链接或管理已有链接（删除使其立即失效）
  - **一次性链接选项**：创建时可选择生成一次性链接（`maxUses=1`），链接使用一次后即失效；默认生成可重复使用的链接（`maxUses=0`）
  
  **4. 一次性链接选项（One-time Link Option）**  
  - **`maxUses`**（可选，默认 `0`）：
    - `0`：无限使用（链接可多次使用直到过期）
    - `1`：一次性链接（使用一次后立即失效，即使未过期也不能再次使用）
    - 前端 UI 在"邀请链接设置"步骤提供开关控制此选项
- **Request (JSON)**（字段来自 `ChatReq`）：

```json
{
  "chatName": "Study Group",
  "avatar": { "objectName": "public/ezchat/xxx/avatar.jpg", "objectUrl": "", "objectThumbUrl": "", "objectId": 456 },
  "joinEnable": 1,
  "joinLinkExpiryMinutes": 10080,
  "maxUses": 0,
  "password": "",
  "passwordConfirm": ""
}
```

- **Response (mock)**（`CreateChatVO`）：

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": { "chatCode": "12345678", "inviteCode": "Ab3X9kQpQW1eR2tZ" }
}
```

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
  - `cursorSeqId`：可选，用于分页，传入上一页最后一条消息的 `seqId`
  - `limit`：可选，默认 30
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
            "objectThumbUrl": "https://minio.example.com/presigned/thumb...",
            "objectId": 789
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
    "objectThumbUrl": "https://minio.example.com/presigned/thumb...",
    "objectId": 789
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

#### `GET /media/check?rawHash=...`（检查对象是否存在 / Check object existence）

- **业务功能 / Purpose**：前端上传前检查对象是否已存在（基于原始文件哈希），用于去重优化  
  Check if object already exists (based on raw file hash) before upload for deduplication.
- **Auth**：需要 `token` / requires `token`
- **Query**：
  - `rawHash`：必填，原始文件的 SHA-256 哈希值（十六进制字符串）
- **Response (mock)**：`data` 为对象信息（如果存在）或 `null`（如果不存在）

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": {
    "objectName": "public/avatars/20000001.jpg",
    "objectUrl": "http://localhost:9000/ezchat/public/avatars/20000001.jpg",
    "objectThumbUrl": "http://localhost:9000/ezchat/public/avatars/20000001_thumb.jpg",
    "objectId": 123
  }
}
```

如果对象不存在，`data` 为 `null`：
If object does not exist, `data` is `null`:

```json
{
  "status": 1,
  "code": 200,
  "message": "success",
  "data": null
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

- **Response**：当前实现返回 **HTTP 200 + 空响应体**（`POST /user`）或 **Result<Void>**（`POST /user/profile`）
  Current implementation returns **HTTP 200 + empty body** (`POST /user`) or **Result<Void>** (`POST /user/profile`).

#### `PUT /user/password`（修改密码 / Change password）

- **业务功能 / Purpose**：修改正式用户密码（需验证旧密码）
  Change formal user password (verify old password).
- **Auth**：需要 `token` / requires `token`
- **Request (JSON)**（字段来自 `UpdatePasswordReq`）：

```json
{
  "oldPassword": "oldPassword123",
  "newPassword": "newPassword456",
  "confirmNewPassword": "newPassword456"
}
```

- **Response**：`Result<Void>`（成功后需重新登录）

#### `POST /user/upgrade`（访客升级 / Guest Upgrade）

- **业务功能 / Purpose**：将当前访客账号升级为正式账号（保留数据）
  Upgrade current guest account to formal account (preserve data).
- **Auth**：需要 `token` / requires `token`
- **Request**：同 `POST /auth/register`
- **Response**：`LoginVO` (new token)

---

### WebSocket 协议 / WebSocket protocol

- **Endpoint**：`/websocket/{token}`（示例：`ws://localhost:8080/websocket/<token>`）
- **心跳 / Heartbeat**：
  - Client → `PING{chatCode}`（例如 `PING20000022`）
  - Server → `PONG`
- **消息封装 / Envelope**：服务端推送消息统一使用 `code` 区分类型：

```json
{
  "code": 1001,
  "message": "MESSAGE",
  "data": { ... }
}
```

- **状态码定义 / Status Codes**：

| Code | Type | Description | Payload (data) |
|---|---|---|---|
| `1001` | `MESSAGE` | 普通聊天消息 / Chat Message | `MessageVO` |
| `2001` | `USER_STATUS` | 用户在线状态变更 / Presence Update | `UserStatus` |
| `2002` | `ACK` | 消息发送确认 / Message Acknowledge | `tempId` (string) |
| `3001` | `MEMBER_JOIN` | 成员加入通知（广播）/ Member Join | `JoinBroadcastVO` |

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
  "code": 1001,
  "message": "MESSAGE",
  "data": {
    "sender": "20000003",
    "chatCode": "20000022",
    "type": 2,
    "text": "hello",
    "images": [ ... ],
    "createTime": "2026-01-02T10:20:30"
  }
}
```

> **Compatible Note**: The legacy fields `isSystemMessage` and `type` (as string) are deprecated but may still exist in some responses for backward compatibility. New implementations should rely on `code`.

---

### 统一响应与异常处理 / Unified response & exception handling

后端所有 REST 接口统一返回 `hal.th50743.pojo.Result<T>`（完全泛型化）：  
All REST APIs return `hal.th50743.pojo.Result<T>` (fully genericized):

- **字段 / Fields**
  - `status`: `1`=success, `0`=failure
  - `code`: 业务码 / business error code（成功固定为 `200`）
  - `message`: 说明信息 / message
  - `data`: 业务数据 / payload（类型为泛型 `T`）

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

---

## 业务逻辑说明 / Business Logic

### 认证与用户管理 / Authentication & User Management

#### 用户类型 / User Types

系统支持两种用户类型：

1. **正式用户 / Formal Users**：
   - 拥有 `username` 和 `password_hash`（存储在 `formal_users` 表）
   - 可通过 `POST /auth/login` 登录
   - 可通过 `POST /auth/register` 注册（支持头像上传）
   - 所有用户共享 `users` 表（统一使用 `uid` 作为对外标识）

2. **访客用户 / Guest Users**：
   - 无 `username`，仅记录 `nickname`
   - 通过 `POST /auth/guest`（Room ID + 密码）或 `POST /auth/invite`（邀请码）创建
   - 可后续通过 `POST /auth/register` 转正（提供 `userUid` 参数）

#### 注册流程 / Registration Flow

- **新用户注册**：创建 `User` + `FormalUser`，生成新的 `uid`
- **临时用户转正**：已有临时 `uid` 的用户，提供 `userUid` 参数，系统创建对应的 `FormalUser` 记录
- **头像处理**：注册时上传的头像会先写入 `objects` 表（status=0, PENDING），注册成功后激活（status=1, ACTIVE）

### 聊天室创建与加入规则 / Chat Room Creation & Join Rules

#### 创建房间 / Create Room

**核心流程**：
1. 生成 8 位数字 `chatCode`（冲突重试最多 5 次）
2. 创建者自动加入房间（写入 `chat_members` 表）
3. 自动生成邀请码（18 位随机字符串，SHA-256 哈希存储）
4. 邀请码默认有效期 7 天（`joinLinkExpiryMinutes=10080`）

**密码加入功能开关（Join with Password）**：
- **开启**（`chat_password_hash != NULL`）：
  - ✅ 可通过 **Room ID + Password** 加入（`POST /auth/guest`）
  - ✅ 可通过 **Invite Link** 加入（绕过密码验证）
- **关闭**（`chat_password_hash = NULL`）：
  - ❌ **不能通过 Room ID 加入**（即使没有密码也不行）
  - ✅ **只能通过 Invite Link 加入**

**全局加入开关（Global Join Switch）**：
- **`join_enabled = 1`**：允许加入（具体方式由密码加入功能开关决定）
- **`join_enabled = 0`**：**禁止所有加入方式**（包括 Room ID + Password 和 Invite Link）

#### 邀请码规则 / Invite Code Rules

**有效期与使用次数**：
- **TTL**：每次发行时必须设定有效期（前端默认 7 天，后端兜底同值）
- **最多 5 条同时生效**：同一房间最多可以有 5 条有效邀请链接同时存在（创建新链接时会检查）
- **一次性链接**：`maxUses=1` 时，链接使用一次后立即失效；`maxUses=0` 时无限使用（直到过期）

**邀请码消费逻辑**：
- 服务端只存储 `code_hash`（SHA-256 哈希），不存储明文
- 消费时原子递增 `used_count`，检查过期/撤销/次数用尽
- 一次性链接（`maxUses=1`）使用后立即失效

**撤销与管理**：
- 可通过设置 `revoked=1` 使邀请码立即失效
- 房间创建后可在 Room Settings 中再次发行新的邀请链接或管理已有链接

#### 加入房间验证流程 / Join Room Validation Flow

**`POST /auth/guest`（Room ID + Password）**：
1. 检查 `join_enabled`：如果为 0，直接拒绝
2. 检查 `chat_password_hash`：
   - 如果 `!= NULL`：必须提供正确密码
   - 如果 `= NULL`：**不能通过此接口加入**（只能通过邀请链接）
3. 创建临时用户并自动加入房间

**`POST /auth/invite`（邀请码免密加入）**：
1. 检查 `join_enabled`：如果为 0，直接拒绝（即使有邀请码也不行）
2. 校验邀请码：检查过期/撤销/次数用尽
3. 消费邀请码：原子递增 `used_count`
4. 创建临时用户并自动加入房间（**免密，即使房间启用了密码加入功能**）

### 图片上传与去重机制 / Image Upload & Deduplication

#### 上传流程 / Upload Flow

**前端预处理**：
1. 使用 Web Crypto API 计算原始文件的 SHA-256 哈希（`raw_object_hash`）
2. 调用 `GET /media/check?rawHash=...` 检查是否已存在
3. 如果已存在，直接复用现有对象（返回 `objectId`），跳过上传
4. 如果不存在，进行前端预压缩（`browser-image-compression`），然后上传

**后端处理**：
1. 接收上传文件，进行规范化处理：
   - **GIF 文件**：完全跳过规范化处理，直接使用原始文件上传（避免丢失动效）
   - **其他图片**：
     - 自动旋转（EXIF Orientation）
     - 去元数据/EXIF（通过重编码输出 JPEG）
     - 统一输出 JPEG（质量约 0.85，最大边 2048）
2. 计算哈希（用于去重比对）：
   - **GIF 文件**：使用原始文件内容的 SHA-256 哈希
   - **其他图片**：使用规范化后内容的 SHA-256 哈希（`normalized_object_hash`）
3. 查询是否存在相同哈希的对象（status=1）
4. 如果存在，复用现有对象（不重复上传到 MinIO）
5. 如果不存在，上传到 MinIO 并写入 `objects` 表（status=0, PENDING）

#### 去重策略 / Deduplication Strategy

**双哈希策略**：
- **`raw_object_hash`**：前端计算的原始文件哈希，用于快速去重检查（`GET /media/check`）
- **`normalized_object_hash`**：后端计算的规范化后哈希，用于最终去重判断

**去重检查顺序**：
1. 前端上传前：先查 `raw_object_hash`（轻量级比对）
2. 后端上传时：先查 `raw_object_hash`，如果不存在再查 `normalized_object_hash`（兼容性）
3. 最终去重：使用 `normalized_object_hash` 确保内容相同但格式不同的文件被识别为重复

#### 默认头像生成机制 / Default Avatar Generation

**头像类型**：
- **用户头像（'user'）**：使用 DiceBear `bottts-neutral` 风格
  - API: `https://api.dicebear.com/9.x/bottts-neutral/svg?seed=${seed}`
  - 用于：用户注册、用户资料、访客模式
- **房间头像（'room'）**：使用 DiceBear `identicon` 风格
  - API: `https://api.dicebear.com/9.x/identicon/svg?seed=${seed}`
  - 用于：聊天室创建、房间头像

**生成时机**：
- **展示阶段**：组件加载时（`onMounted`）生成默认头像 URL，仅用于展示，不上传
- **上传阶段**：表单提交时，如果用户未上传头像，自动上传默认头像到服务器

**实现位置**：
- `imageStore.generateDefaultAvatarUrl(type, seed?)`：生成默认头像 URL
- `imageStore.uploadDefaultAvatarIfNeeded(avatar, type?)`：按需上传默认头像
- 所有头像上传器（注册、创建房间、访客加入）都支持默认头像显示和自动上传

#### 对象生命周期管理 / Object Lifecycle Management

**状态流转**：
- **PENDING（status=0）**：上传后初始状态，等待激活
- **ACTIVE（status=1）**：已激活，关联到用户/聊天室/消息

**激活时机**：
- **用户头像**：注册/更新用户信息时激活（如果未上传头像，会自动上传默认头像后激活）
- **聊天室封面**：创建聊天室时激活（如果未上传头像，会自动上传默认头像后激活）
- **消息图片**：消息保存成功后批量激活（`fileService.activateFilesBatch()`）

**垃圾回收（GC）**：
- **定时任务**：每天凌晨 2 点执行（`@Scheduled(cron = "0 0 2 * * ?")`）
- **清理条件**：`status=0 AND create_time < NOW - 24h`（24 小时前的 PENDING 文件）
- **处理策略**：
  - 分页查询（每批 100 条），防止 OOM
  - 每批处理后休眠 100ms，释放数据库连接
  - 异常隔离：单个文件删除失败不影响其他文件
  - 先删除 MinIO 对象（自动删除缩略图），再删除数据库记录

### 消息处理逻辑 / Message Processing Logic

#### 消息类型计算 / Message Type Calculation

后端自动计算消息类型（并在 DB 中持久化）：
- **`type=0`**：仅文本（`text != null && !text.isBlank()` 且 `images == null || images.isEmpty()`）
- **`type=1`**：仅图片（`images != null && !images.isEmpty()` 且 `text == null || text.isBlank()`）
- **`type=2`**：混合（文本 + 图片都存在）

#### 消息图片存储 / Message Image Storage

**存储格式**：
- `messages.object_ids` 字段存储 JSON 数组格式（如 `[1,2,3]`），存储 `objects.id` 列表
- 序列化：`List<Integer>` → JSON 字符串（使用 `ObjectMapper.writeValueAsString()`）
- 反序列化：JSON 字符串 → `List<Integer>` → 查询 `objects` 表构建 `Image` 对象列表

**图片关联流程**：
1. 前端上传图片，获得 `Image` 对象（包含 `objectId`）
2. 发送消息时，传递 `Image` 对象列表（包含 `objectId`）
3. 后端序列化 `objectId` 列表为 JSON 字符串，存储到 `messages.object_ids`
4. 查询消息时，反序列化 `object_ids`，根据 `objectId` 查询 `objects` 表构建 `Image` 对象列表

#### 已读游标更新 / Read Cursor Update

- 查询消息列表时（`GET /message?chatCode=...&timeStamp=...`），服务端会同步更新当前用户在该房间的 `last_seen_at`
- 用于计算未读消息数（`messages.create_time > chat_members.last_seen_at`）

### 对象关联设计 / Object Association Design

系统统一使用**逻辑外键**关联 `objects` 表，避免通过 `objectName` 查询，提升性能：

**关联字段**：
- **`users.object_id`**：关联用户头像对象（`objects.id`）
- **`chats.object_id`**：关联聊天室头像对象（`objects.id`）
- **`messages.object_ids`**：关联消息图片对象列表（JSON 数组格式，如 `[1,2,3]`，存储 `objects.id` 列表）

**性能优化**：
- **上传接口返回 `objectId`**：所有图片上传接口（`POST /auth/register/upload`、`POST /message/upload`、`GET /media/check`）都会返回 `Image` 对象，包含 `objectId` 字段
- **前端直接传递 `objectId`**：创建聊天室/发送消息时，前端直接传递 `objectId`，后端无需查表
- **查询时使用 JOIN**：查询聊天室列表/详情时，使用 `LEFT JOIN objects` 获取 `object_name`，然后构建 `Image` 对象

**数据一致性**：
- 不使用物理外键约束，数据一致性由应用层保证
- 创建聊天室/发送消息时，如果 `objectId` 为 `null`，会记录错误日志（新工程中上传接口必须返回 `objectId`）

### 测试数据生成 / Test Data Generation

**种子数据**：
- `init.sql` 包含 30 条真实头像数据（ID 1-30），存储在 `objects` 表
- 所有用户头像、聊天室封面、消息图片都从这 30 条数据中随机选择

**生成规则**：
- **用户**：100 个用户，每个用户随机选择一个种子头像
- **聊天室**：20 个聊天室，每个聊天室克隆一个种子数据作为封面（category=CHAT_COVER）
- **消息**：每个聊天室 50 条消息，20% 概率是图片消息，图片从种子数据中随机选择并克隆（category=MESSAGE_IMG）
- **成员关系**：所有 100 个用户加入所有 20 个聊天室（全员入全群）

**垃圾数据**：
- 生成 10 条 PENDING 状态的垃圾对象（48 小时前创建），用于测试 GC 功能

---

## 更新日志 / Changelog

### 2026-01-10
- **Features & UI**:
  - **User Settings**: Implemented comprehensive User Settings Dialog (Profile update, Password management for formal users).
  - **Premium UI**: Refined Dropdown styles (solid background, rounded corners) and centered Loading indicators in MessageArea.
  - **Guest Experience**: Extended guest cleanup threshold to 2 hours (was 10 min) for better retention.
- **Backend Refactoring**:
  - **Log Translation**: Fully internationalized all backend logs to English for better maintainability.
  - **API Updates**: Added `PUT /user/password` and optimized `POST /user/profile` endpoints.

### 2026-01-09
- **Architecture**:
  - **Dual Token Auth**: Implemented Access/Refresh token mechanism with caching (Caffeine).
  - **Auto-Cleanup**: Added `GuestCleanupService` to remove inactive guest accounts automatically.
  - **Audit Logging**: Added AOP-based operation logging (`OperationLog` table) for all CUD operations. 
  - **SeqId Pagination**: Replaced timestamp-based pagination with stable `seq_id` cursor pagination.
  - **Security Hardening**: Removed `accessToken` persistence in localStorage (memory-only) to reduce XSS risk.
- **Frontend**:
  - **Source Refactor**: Renamed `hooks` directory to `composables` to align with Vue 3 naming conventions.
  - **Refresh Logic**: Implemented 401 retry lock and background token refresh.
  - **Login Feedback**: Enhanced login error feedback (alerting failures directly on home page).
  - **Room Settings**: 
    - Added "Password Enabled" flag to public room info for accurate UI status.
    - Redesigned Password Settings UI (Switch-driven, edit mode, conditional inputs).
    - Improved Member Management (Per-row actions for Transfer/Kick, optimized list layout).
  - **UI Polish**: Optimized flip card animations (0.7s) and verified light/dark mode consistency.
  - **I18n**: Completed missing translations for Room Settings and Member Management alerts.

### 2026-01-07
- **Refactoring**:
  - WebSocket Protocol refactored to use Status Codes (`1001/2001/2002/3001`) instead of string types.
  - Decoupled `ChatService` logic: Extracted `ChatMemberAssembler` and `MsgAssembler`.
  - Moved generic business logic from `AuthService` to `ChatService` (e.g., `joinChat`).
- **Features**:
  - **Complete I18n**: Added support for System Messages, Invite Flows, and Error Toasts (zh/en/ja/ko/zh-tw).
  - **Runtime Validation**: Implemented Zod-like validators for WebSocket messages and Invite inputs.
  - **Enhanced Guest UX**: Improved guest join flow with clear error messages for missing room IDs or invalid codes.
  - **Presence Debounce**: Implemented 30s buffer for offline broadcasts to prevent flickering during network instability.
- **Bug Fixes**:
  - Fixed "Infinite Loading" on joining existing rooms.
  - Fixed `MyBatis BindingException` in `UserMapper`.
  - Fixed `NullPointerException` (TypeScript) in `MessageItem.vue` for system messages without images.
  - Fixed Guest Broadcast not delivering to existing members.
