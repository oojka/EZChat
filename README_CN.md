[English](./README.md) | [日本語](./README_JA.md)

# EZChat

一个基于 **Spring Boot 3 + Vue 3** 的现代化实时聊天系统：支持 WebSocket 实时消息、好友系统、私聊、访客/注册登录、在线状态、图片上传与缩略图、国际化与暗黑模式。

---

## 功能特性

- **好友系统**：搜索 UID 添加好友、双向确认机制、好友列表与在线状态展示
- **私聊**：基于好友关系的 1v1 私聊，自动创建私密房间
- **实时消息**：WebSocket 双向通信（消息广播、心跳、ACK）
- **认证**：双 Token 机制（Access/Refresh）+ 自动刷新，支持注册登录与访客加入
- **自动清理**：自动清理长期离线的访客账号及其数据
- **聊天室**：创建房间（可使用密码加入/邀请链接/一次性链接）、通过 chatCode 获取房间信息并进入聊天
- **用户设置**：更新个人资料（头像/昵称/Bio）、修改密码（仅正式用户）
- **消息排序**：基于 Sequence ID 的精确消息排序与分页
- **在线状态**：上线/离线广播
- **状态防抖**：30s 离线缓冲，防止网络波动造成误报
- **操作审计**：全量操作日志记录，支持安全审计
- **图片上传**：上传图片，按需生成缩略图（仅超阈值才生成），统一大小限制 10MB
- **默认头像生成**：使用 DiceBear API 自动生成默认头像（用户使用 bottts-neutral，房间使用 identicon），未上传头像时自动使用
- **图片去重**：双哈希策略（前端预计算 + 后端规范化哈希）防止重复上传，节省存储空间
- **图片优化**：前端预压缩（提升上传体验）+ 后端规范化（兼容/隐私）
- **刷新体验优化**：refresh 时优先加载 chatList，成员/消息按需并行加载，减少黑屏与等待
- **国际化**：`zh/en/ja/ko/zh-tw` 全面覆盖（含系统消息与错误提示）
- **类型安全**：运行时校验（自定义 type guard）+ TypeScript 严格模式
- **暗黑模式**：Element Plus 暗黑变量

---

## 技术栈

### 后端

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

### 前端

- **Vue**: 3.5.25
- **TypeScript**: 5.9.x
- **Vite**: 7.2.x
- **Pinia**: 3.0.x
- **Vue Router**: 4.6.x
- **Element Plus**: 2.12.x
- **Axios**: 1.13.x

---

## 快速开始

### 环境要求

- **Backend**：JDK 17+、Maven 3.6+、MySQL 8.x、MinIO（当前配置下为必需）
- **Frontend**：Node.js `^20.19.0 || >=22.12.0`、npm 10+

### 数据库结构

本项目数据库初始化脚本位于 `backend/EZChat-app/src/main/resources/sql/init.sql`，当前结构使用 **MySQL 8** + **utf8mb4**。

> ⚠️ 重要
> `init.sql` 会 `DROP TABLE IF EXISTS` 并在存储过程中 `TRUNCATE` 多张表，然后生成大量测试数据（多语言昵称、房间、消息）。请勿用于生产库。

#### 表一览

- `users`：所有用户（访客/正式用户共用），含 `user_type`
- `formal_users`：正式用户账号体系（用户名/密码哈希）
- `chats`：聊天室（群/单聊统一存储）
- `chat_members`：聊天室成员关系 + 最后阅读时间
- `chat_sequences`：**[NEW]** 聊天室消息序列号表（当前最大 seq_id）
- `chat_invites`：聊天室邀请码（短链接加入权限，含 TTL / 次数 / 撤销）
- `messages`：消息主体（含 `seq_id` + `asset_ids`）
- `assets`：**[NEW]** 核心资产表（原 `objects`），存储图片/文件元数据（MinIO）
- `friendships`：**[NEW]** 好友关系表（双向记录）
- `friend_requests`：**[NEW]** 好友申请表（状态：Pending/Accepted/Rejected）
- `operation_logs`：操作审计日志

#### 字段要点

`friendships`

| 字段 | 含义 |
|---|---|
| `id` (PK) | 关系ID |
| `user_id` | 用户ID (我) |
| `friend_id` | 好友ID (他) |
| `alias` | 备注名 |

`chats` (Updated)

| 字段 | 含义 |
|---|---|
| `id` (PK) | 聊天室内部ID |
| `type` | **[NEW]** 0=群聊(Group), 1=私聊(Private) |
| `chat_code` (UNIQUE) | 聊天室对外ID |
| `max_members` | 成员上限 (默认 200, 私聊为 2) |

`assets` (was `objects`)

| 字段 | 含义 |
|---|---|
| `id` (PK) | 资产ID |
| `asset_name` | MinIO 对象名 (原 object_name) |
| `original_name` | 原始文件名 |
| `category` | 分类 (USER_AVATAR/CHAT_COVER...) |
| `message_id` | 关联消息ID (复用模式下可能为空) |
| `raw_asset_hash` | 原始文件哈希 (SHA-256) |
| `normalized_asset_hash` | 规范化后哈希 (SHA-256) |

#### 测试数据生成

`init.sql` 会创建并执行存储过程 `generate_test_data()`，默认会：

- 插入 **30 条种子数据**（真实头像，ID 1-30）到 `assets` 表
- 插入 **100** 个用户（每个用户随机选择一个种子头像）
- 插入 **100** 个正式用户凭证
- 创建 **20** 个聊天室（每个聊天室引用一个种子数据作为封面）
- 为每个房间生成 **50** 条消息（20% 概率是图片消息，引用种子数据）
- **所有用户加入所有聊天室**（全员入全群）
- **创建部分好友关系**：让 User 1 与 User 2/3 成为好友，并生成一些待处理的好友申请
- 生成 **10 条垃圾数据**（PENDING 状态，48 小时前创建，用于测试 GC）

### 1) 初始化数据库

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ezchat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p ezchat < backend/EZChat-app/src/main/resources/sql/init.sql
```

### 2) 配置环境变量

后端 `backend/EZChat-app/src/main/resources/application.yml` 全部使用 `${ENV}` 占位符，**必须**注入环境变量（本工程未集成 `.env` 自动加载）。

```bash
export DB_URL='jdbc:mysql://localhost:3306/ezchat?useSSL=false&serverTimezone=Asia/Tokyo'
export DB_USERNAME='root'
export DB_PASSWORD='your_password'

export JWT_SECRET='your_jwt_secret_key_at_least_256_bits'

export OSS_ENDPOINT='http://localhost:9000'
export OSS_ACCESS_KEY='minioadmin'
export OSS_SECRET_KEY='minioadmin'
export OSS_BUCKET_NAME='ezchat'
export OSS_PATH='images'
```

> 提示
> 使用 IntelliJ IDEA 启动后端时，请在 Run Configuration 中配置同名环境变量。

### 3) 启动后端

```bash
cd backend/EZChat-parent
mvn -q -pl ../EZChat-app spring-boot:run
```

默认端口: **8080**

### 4) 启动前端

```bash
cd frontend/vue-ezchat
npm install
npm run dev
```

访问: `http://localhost:5173`

---

## 业务逻辑说明

### 好友系统 (NEW)

#### 申请与确认
- **双向确认制**：A 向 B 发送申请 -> B 同意 -> 双方成为好友。
- **状态流转**：`0=Pending` (待处理), `1=Accepted` (已同意), `2=Rejected` (已拒绝)。
- **限制**：不能重复发送申请；不能添加自己；不能添加已是好友的用户。

#### 私聊
- **基于好友关系**：只有好友之间才能发起私聊。
- **自动创建**：点击好友发起聊天时，系统检查是否存在共同的私聊房间（Type=1）。如果不存在，自动创建一个包含双方的私聊房间（Type=1, MaxMembers=2）。
- **房间复用**：如果两人之间已存在私聊房间，直接复用，不重复创建。

### 认证与用户管理

#### 用户类型

系统支持两种用户类型：

1. **正式用户**：
   - 拥有 `username` 和 `password_hash`（存储在 `formal_users` 表）
   - 可通过 `POST /auth/login` 登录
   - 可通过 `POST /auth/register` 注册（支持头像上传）
   - 所有用户共享 `users` 表（统一使用 `uid` 作为对外标识）
   - **仅正式用户可使用好友系统**。

2. **访客用户**：
   - 无 `username`，仅记录 `nickname`
   - 通过 `POST /auth/join` 创建（密码模式：`chatCode + password`；邀请码模式：`inviteCode`）
   - 可后续通过 `POST /user/upgrade` 转正

### 图片上传与去重机制

#### 上传流程

**前端预处理**：
1. 使用 Web Crypto API 计算原始文件的 SHA-256 哈希（`raw_asset_hash`）
2. 调用 `GET /media/check?rawHash=...` 检查是否已存在
3. 如果已存在，直接复用现有对象（返回 `assetId`），跳过上传
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
   - **其他图片**：使用规范化后内容的 SHA-256 哈希（`normalized_asset_hash`）
3. 查询是否存在相同哈希的对象（status=1）
4. 如果存在，复用现有对象（不重复上传到 MinIO）
5. 如果不存在，上传到 MinIO 并写入 `assets` 表（status=0, PENDING）

### 对象关联设计

系统统一使用**逻辑外键**关联 `assets` 表，避免通过 `assetName` 查询，提升性能：

**关联字段**：
- **`users.asset_id`**：关联用户头像对象（`assets.id`）
- **`chats.asset_id`**：关联聊天室头像对象（`assets.id`）
- **`messages.asset_ids`**：关联消息图片对象列表（JSON 数组格式，如 `[1,2,3]`，存储 `assets.id` 列表）

**性能优化**：
- **上传接口返回 `assetId`**：所有图片上传接口都会返回 `Image` 对象，包含 `assetId` 字段
- **前端直接传递 `assetId`**：创建聊天室/发送消息时，前端直接传递 `assetId`，后端无需查表

---

## 移动端架构 (NEW)

移动端认证流程已重构为多页面架构，提供更流畅的沉浸式体验。

### 路由结构

- `/m` - 欢迎页 (3个入口卡片)
- `/m/guest` - 访客加入 (房间号+密码 / 邀请码)
- `/m/login` - 用户登录
- `/m/register` - 用户注册 (两步向导)

### 核心组件

位于 `frontend/vue-ezchat/src/views/mobile/entry/`：

- `MobileEntryShell.vue`: 共享布局组件
- `MobileWelcomeView.vue`: 欢迎页
- `MobileGuestJoinView.vue`: 访客加入
- `MobileLoginView.vue`: 登录
- `MobileRegisterView.vue`: 注册

### 设计亮点

- **高级玻璃拟态**
- **动态背景**: 漂浮光球效果
- **响应式头部**: 键盘弹出时自动折叠
- **暗黑模式支持**
- **移动端优先触控**

---

## API 接口文档

### 好友系统 (`/friend/*`)

- **GET /friend/list**: 获取好友列表
- **GET /friend/requests**: 获取待处理的好友申请
- **POST /friend/request**: 发送好友申请
- **POST /friend/handle**: 处理好友申请（同意/拒绝）
- **POST /friend/chat**: 获取或创建私聊（返回 chatCode）
- **DELETE /friend/{friendUid}**: 删除好友
- **PUT /friend/alias**: 修改好友备注

---

## 更新日志

### 2026-01-11 (Latest)
- **Features**:
  - **好友系统**: 实现了完整的好友生命周期（申请、接受、列表、删除）。
  - **私聊**: 支持 1v1 私聊，包含自动创建逻辑。
  - **移动端认证**: 重构了移动端认证布局（从 Tab 式改为多页面 `/m/*`），采用玻璃拟态设计。
  - **UI 集成**: 侧边栏新增“好友”标签；新增移动端入口组件（`MobileEntryShell`, `MobileWelcomeView` 等）。
- **Backend**:
  - **Schema 更新**: 新增 `friendships`, `friend_requests` 表，更新 `chats` 表。
  - **逻辑**: 实现了 `FriendService` 并扩展了 `ChatService` 以支持私聊。

### 2026-01-10
- **Features & UI**:
  - **用户设置**: 实现了全面的用户设置对话框（资料更新、正式用户修改密码）。
  - **UI 优化**: 优化了下拉菜单样式（实色背景、圆角），消息区域加载指示器居中。
  - **访客体验**: 将访客清理阈值延长至 2 小时（原为 10 分钟），以提高留存率。
- **Backend Refactoring**:
  - **日志翻译**: 将所有后端日志全面国际化为英语，以提高可维护性。
  - **API 更新**: 新增 `PUT /user/password` 并优化了 `POST /user/profile` 接口。

### 2026-01-09
- **Architecture**:
  - **双 Token 认证**: 实现了 Access/Refresh 令牌机制及缓存（Caffeine）。
  - **自动清理**: 新增 `GuestCleanupService` 自动清理不活跃的访客账号。
  - **操作审计**: 为所有增删改操作添加了基于 AOP 的操作日志（`OperationLog` 表）。
  - **SeqId 分页**: 用稳定的 `seq_id` 游标分页取代了基于时间戳的分页。
  - **安全加固**: 移除了 localStorage 中的 `accessToken` 持久化（仅内存），以降低 XSS 风险。

---
*Last Updated: 2026-01-11*
