## vue-ezchat（前端）/ Frontend

本目录是 **EZChat** 的前端工程（Vue 3 + Vite + TypeScript + Element Plus）。
更完整的项目说明（后端/数据库/MinIO/SQL 初始化）请看仓库根目录的 `README.md`。

This folder contains the **EZChat frontend** (Vue 3 + Vite + TypeScript + Element Plus).
For the full project guide (backend/database/MinIO/SQL init), see the repository root `README.md`.

---

## 环境要求 / Requirements

- **Node.js**: `^20.19.0 || >=22.12.0` (see `package.json`)
- **npm**: 10+

---

## 安装与启动 / Install & Run

```bash
cd frontend/vue-ezchat
npm install
npm run dev
```

默认开发地址 / Dev URL:
- `http://localhost:5173`

---

## 代理与端口 / Proxy & Ports

Vite 代理配置见 `vite.config.ts`：

- **HTTP API**: `/api/...` → `http://localhost:8080/...`（rewrite 去掉 `/api`）
- **WebSocket**: `/websocket/...` → `ws://localhost:8080/websocket/...`

如果你在外层套了 HTTPS 反代，HMR 已配置为 `wss`（见 `vite.config.ts` 的 `server.hmr`）。

---

## 当前工程约定 / Current Conventions

### 1) 用户字段命名 / User id field

- 前后端统一使用 **`uid`**（小写）作为用户对外标识字段
- 消息发送者 `msg.sender` 与成员 `chatMembers[].uid` 匹配

### 2) 鉴权 Token 传递 / Auth token

后端鉴权使用请求头 `token`（不是 `Authorization: Bearer ...`）。
前端在 `src/utils/request.ts` 的 request interceptor 中从 `localStorage.loginUser.token` 写入：

- `config.headers.token = loginUser.token`

### 3) 头像显示回退 / Avatar fallback

聊天消息头像实现三段式回退（缩略图 → 原图 → 文本）：

- Thumb：`objectThumbUrl`
- Thumb 加载失败自动切换 Original：`objectUrl`
- Original 也失败：显示昵称首字母

对应实现位置：
- `src/views/chat/components/MessageItem.vue`（`<el-avatar @error="handleAvatarError">`）

### 4) Hotlink 兼容 / Referrer policy

为降低图片外链防盗链导致的 403，入口已设置：

- `index.html`: `<meta name="referrer" content="no-referrer" />`

### 5) Web Fonts（中日文混排一致性）/ Web fonts for CJK consistency

已在 `index.html` 引入：
- `Noto Sans JP` / `Noto Sans SC`

---

## 常用脚本 / Scripts

```bash
npm run dev        # 开发 / dev
npm run build      # 构建（含 type-check）/ build (includes type-check)
npm run type-check # 仅类型检查 / type check only
npm run lint       # ESLint 自动修复 / eslint --fix
```

---

## 推荐 IDE / Recommended IDE

- VS Code + Volar（禁用 Vetur）/ VS Code + Volar (disable Vetur)
- Cursor（配合 `.cursorrules`）/ Cursor (with `.cursorrules`)


