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

### 1) 初始化数据库 / Initialize database

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ezchat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p ezchat < backend/EZChat-app/src/main/resources/sql/init.sql
```

> 注意 / Note  
> `init.sql` 可能包含清表/测试数据逻辑，请勿用于生产库。  
> `init.sql` may clear tables / generate test data. Do NOT use in production.

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

### 主要后端路由 / Main backend routes

> 说明 / Note：以下路径均是**后端真实路径**（不含 `/api` 前缀）。  
> These are backend paths (no `/api` prefix).

- `POST /auth/login`
- `POST /auth/register`
- `POST /auth/guest`
- `POST /auth/register/upload`
- `GET  /init`
- `GET  /chat/{chatCode}`
- `GET  /message?chatCode=...&timeStamp=...`
- `POST /message/upload`
- `GET  /user/{uid}`
- `POST /user`

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


