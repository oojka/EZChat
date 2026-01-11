# EZChat / EZ Chat

一个基于 **Spring Boot 3 + Vue 3** 的现代化实时聊天系统：支持 WebSocket 实时消息、好友系统、私聊、访客/注册登录、在线状态、图片上传与缩略图、国际化与暗黑模式。

A modern real-time chat system built with **Spring Boot 3 + Vue 3**: WebSocket messaging, friend system, direct messaging, guest/registered auth, presence, image upload + thumbnails, i18n, and dark mode.

---

## 功能特性 / Features

- **好友系统 / Friend System**：搜索 UID 添加好友、双向确认机制、好友列表与在线状态展示 / Search UID to add friends, bi-directional confirmation, friend list & presence
- **私聊 / Direct Messaging**：基于好友关系的 1v1 私聊，自动创建私密房间 / Friend-based 1v1 chat, auto-creates private rooms
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

- **Java**: 17
- **Spring Boot**: 3.3.4
- **WebSocket**: Jakarta WebSocket + `@ServerEndpoint`
- **MyBatis**: 3.0.3
- **PageHelper**: 2.1.0 (Integration with MyBatis)
- **MySQL**: 8.x
- **JWT**: `jjwt` 0.11.5
- **Spring Security Crypto**: 6.3.4 (BCrypt)
- **Cache**: Caffeine (Local Cache)
- **Object Storage**: MinIO（自研 starter：`minio-oss-spring-boot-starter` 0.0.5-SNAPSHOT）
- **Thumbnail**: Thumbnailator 0.4.20
- **AOP**: Spring AOP (Process Logging)

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

- **Backend**：JDK 17+、Maven 3.6+、MySQL 8.x、MinIO（当前配置下为必需）  
  **Backend**: JDK 17+, Maven 3.6+, MySQL 8.x, MinIO (required by current config)
- **Frontend**：Node.js `^20.19.0 || >=22.12.0`、npm 10+  
  **Frontend**: Node.js `^20.19.0 || >=22.12.0`, npm 10+

### 数据库结构 / Database schema

本项目数据库初始化脚本位于 `backend/EZChat-app/src/main/resources/sql/init.sql`，当前结构使用 **MySQL 8** + **utf8mb4**。  
Schema is defined in `backend/EZChat-app/src/main/resources/sql/init.sql` (MySQL 8 + utf8mb4).

> ⚠️ 重要 / Important  
> `init.sql` 会 `DROP TABLE IF EXISTS` 并在存储过程中 `TRUNCATE` 多张表，然后生成大量测试数据（多语言昵称、房间、消息）。请勿用于生产库。  
> `init.sql` drops tables, truncates data inside stored procedure, and generates lots of test data. Do NOT use in production.

#### 表一览 / Tables

- `users`：所有用户（访客/正式用户共用），含 `user_type` / all users (guest + formal)
- `formal_users`：正式用户账号体系（用户名/密码哈希）/ credentials for formal users
- `chats`：聊天室（群/单聊统一存储）/ chat rooms
- `chat_members`：聊天室成员关系 + 最后阅读时间 / membership + last_seen_at
- `chat_sequences`：**[NEW]** 聊天室消息序列号表（当前最大 seq_id）/ chat message sequence counter
- `chat_invites`：聊天室邀请码（短链接加入权限，含 TTL / 次数 / 撤销）/ invite codes
- `messages`：消息主体（含 `seq_id` + `asset_ids`） / messages
- `assets`：**[NEW]** 核心资产表（原 `objects`），存储图片/文件元数据（MinIO）/ core assets table (was `objects`)
- `friendships`：**[NEW]** 好友关系表（双向记录）/ bi-directional friendships
- `friend_requests`：**[NEW]** 好友申请表（状态：Pending/Accepted/Rejected）/ friend requests
- `operation_logs`：操作审计日志 / operation audit logs

#### 字段要点 / Key fields (summary)

`friendships`

| 字段 | 含义 (中文) | Meaning (EN) |
|---|---|---|
| `id` (PK) | 关系ID | relationship id |
| `user_id` | 用户ID (我) | user id (me) |
| `friend_id` | 好友ID (他) | friend id (them) |
| `alias` | 备注名 | alias/remark |

`chats` (Updated)

| 字段 | 含义 (中文) | Meaning (EN) |
|---|---|---|
| `id` (PK) | 聊天室内部ID | internal chat id |
| `type` | **[NEW]** 0=群聊(Group), 1=私聊(Private) | 0=Group, 1=Private |
| `chat_code` (UNIQUE) | 聊天室对外ID | public chat code |
| `max_members` | 成员上限 (默认 200, 私聊为 2) | max members |

`assets` (was `objects`)

| 字段 | 含义 (中文) | Meaning (EN) |
|---|---|---|
| `id` (PK) | 资产ID | asset id |
| `asset_name` | MinIO 对象名 (原 object_name) | MinIO object name |
| `original_name` | 原始文件名 | original file name |
| `category` | 分类 (USER_AVATAR/CHAT_COVER...) | category |
| `message_id` | 关联消息ID (复用模式下可能为空) | linked message id |
| `raw_asset_hash` | 原始文件哈希 (SHA-256) | raw file hash |
| `normalized_asset_hash` | 规范化后哈希 (SHA-256) | normalized hash |

#### 测试数据生成 / Test data generation

`init.sql` 会创建并执行存储过程 `generate_test_data()`，默认会：

`init.sql` creates and calls `generate_test_data()`, which by default:

- 插入 **30 条种子数据**（真实头像，ID 1-30）到 `assets` 表 / inserts **30 seed assets** into `assets` table
- 插入 **100** 个用户（每个用户随机选择一个种子头像）/ inserts **100** users
- 插入 **100** 个正式用户凭证 / inserts **100** formal user credentials
- 创建 **20** 个聊天室（每个聊天室引用一个种子数据作为封面）/ creates **20** chats (referenced seed asset as cover)
- 为每个房间生成 **50** 条消息（20% 概率是图片消息，引用种子数据）/ generates **50** messages per chat (20% image messages, referencing seeds)
- **所有用户加入所有聊天室**（全员入全群）/ all users join all chats
- **创建部分好友关系**：让 User 1 与 User 2/3 成为好友，并生成一些待处理的好友申请 / creates some friendships and pending requests for User 1
- 生成 **10 条垃圾数据**（PENDING 状态，48 小时前创建，用于测试 GC）/ generates **10 garbage assets** for GC testing

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

## 业务逻辑说明 / Business Logic

### 好友系统 / Friend System (NEW)

#### 申请与确认 / Request & Confirmation
- **双向确认制**：A 向 B 发送申请 -> B 同意 -> 双方成为好友。
- **状态流转**：`0=Pending` (待处理), `1=Accepted` (已同意), `2=Rejected` (已拒绝)。
- **限制**：不能重复发送申请；不能添加自己；不能添加已是好友的用户。

#### 私聊 / Private Chat
- **基于好友关系**：只有好友之间才能发起私聊。
- **自动创建**：点击好友发起聊天时，系统检查是否存在共同的私聊房间（Type=1）。如果不存在，自动创建一个包含双方的私聊房间（Type=1, MaxMembers=2）。
- **房间复用**：如果两人之间已存在私聊房间，直接复用，不重复创建。

### 认证与用户管理 / Authentication & User Management

#### 用户类型 / User Types

系统支持两种用户类型：

1. **正式用户 / Formal Users**：
   - 拥有 `username` 和 `password_hash`（存储在 `formal_users` 表）
   - 可通过 `POST /auth/login` 登录
   - 可通过 `POST /auth/register` 注册（支持头像上传）
   - 所有用户共享 `users` 表（统一使用 `uid` 作为对外标识）
   - **仅正式用户可使用好友系统**。

2. **访客用户 / Guest Users**：
   - 无 `username`，仅记录 `nickname`
   - 通过 `POST /auth/guest`（Room ID + 密码）或 `POST /auth/invite`（邀请码）创建
   - 可后续通过 `POST /auth/register` 转正（提供 `userUid` 参数）

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

### 对象关联设计 / Object Association Design

系统统一使用**逻辑外键**关联 `assets` 表，避免通过 `assetName` 查询，提升性能：

**关联字段**：
- **`users.asset_id`**：关联用户头像对象（`assets.id`）
- **`chats.asset_id`**：关联聊天室头像对象（`assets.id`）
- **`messages.asset_ids`**：关联消息图片对象列表（JSON 数组格式，如 `[1,2,3]`，存储 `assets.id` 列表）

**性能优化**：
- **上传接口返回 `assetId`**：所有图片上传接口都会返回 `Image` 对象，包含 `assetId` 字段
- **前端直接传递 `assetId`**：创建聊天室/发送消息时，前端直接传递 `assetId`，后端无需查表

---

## API 接口文档 / API Reference

### 好友系统 / Friend System (`/friend/*`)

- **GET /friend/list**: 获取好友列表 / Get friend list
- **GET /friend/requests**: 获取待处理的好友申请 / Get pending requests
- **POST /friend/request**: 发送好友申请 / Send friend request
- **POST /friend/handle**: 处理好友申请（同意/拒绝）/ Accept or reject request
- **POST /friend/chat**: 获取或创建私聊（返回 chatCode）/ Get or create private chat
- **DELETE /friend/{friendUid}**: 删除好友 / Delete friend
- **PUT /friend/alias**: 修改好友备注 / Update friend alias

---

## 更新日志 / Changelog

### 2026-01-11 (Latest)
- **Features**:
  - **Friend System**: Implemented full friend lifecycle (Request, Accept, List, Remove).
  - **Private Chat**: Added support for 1v1 direct messaging with auto-creation logic.
  - **UI Integration**: Added "Friends" tab in sidebar, friend list view, and add friend dialog.
- **Backend**:
  - **Schema Update**: Added `friendships`, `friend_requests` tables and updated `chats` table.
  - **Logic**: Implemented `FriendService` and extended `ChatService` for private chats.

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
