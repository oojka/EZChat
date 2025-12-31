# EZChat - 实时聊天系统

<div align="center">

![Logo](https://img.shields.io/badge/EZChat-v1.0-blue?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-green?style=flat-square)
![Vue](https://img.shields.io/badge/Vue-3.5.25-brightgreen?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

一个基于 WebSocket 的现代化实时聊天系统，支持多人群聊、图片消息、在线状态同步等功能。

</div>

---

## 📌 项目定位

**EZChat** 是一个企业级实时聊天系统，采用前后端分离架构，基于 WebSocket 实现消息实时推送。系统支持：

- 🚀 **实时通信** - WebSocket 长连接，毫秒级消息送达
- 👥 **多人群聊** - 支持创建聊天室、邀请成员、权限管理
- 📷 **富媒体消息** - 文本、图片（含缩略图生成）、Emoji 表情
- 🌐 **国际化** - 内置中文/英文/日文/韩文/繁体中文 5 语言支持
- 🔐 **安全认证** - JWT Token 鉴权 + 前端限流机制
- 🌓 **主题切换** - 亮色/暗色模式自动适配

---

## 🛠️ 技术栈

### 后端 (Backend)

| 技术           | 版本    | 说明                          |
| -------------- | ------- | ----------------------------- |
| Java           | 21      | 核心语言，使用 Records 等新特性 |
| Spring Boot    | 3.3.4   | 企业级应用框架                |
| Spring WebSocket | -     | WebSocket 支持                |
| MyBatis        | 3.0.3   | 持久层框架                    |
| MySQL          | -       | 关系型数据库                  |
| JWT            | 0.11.5  | Token 认证                    |
| MinIO          | 自定义   | 对象存储（图片文件）          |
| Thumbnailator  | 0.4.20  | 图片缩略图生成                |
| Lombok         | 1.18.34 | 代码简化                      |

**架构模式**: Controller → Service → Mapper (三层架构)

### 前端 (Frontend)

| 技术           | 版本    | 说明                          |
| -------------- | ------- | ----------------------------- |
| Vue            | 3.5.25  | 渐进式 JavaScript 框架        |
| TypeScript     | 5.9.0   | 类型安全                      |
| Pinia          | 3.0.4   | 状态管理（Vuex 5）            |
| Vue Router     | 4.6.3   | 路由管理                      |
| Element Plus   | 2.12.0  | UI 组件库                     |
| Axios          | 1.13.2  | HTTP 客户端                   |
| Vue I18n       | 11.2.7  | 国际化                        |
| Vite           | 7.2.4   | 构建工具                      |

**编码规范**: Composition API + `<script setup>` + TypeScript

---

## 📁 项目结构

```
EZChat/
├── backend/                        # 后端代码目录
│   ├── EZChat-parent/              # Maven 父工程
│   │   └── pom.xml                 # 依赖版本管理
│   ├── EZChat-app/                 # 主应用模块
│   │   ├── src/main/java/hal/th50743/
│   │   │   ├── config/             # 配置类（WebSocket, CORS）
│   │   │   ├── controller/         # 控制器层（API 接口）
│   │   │   ├── service/            # 服务层（业务逻辑）
│   │   │   ├── mapper/             # 持久层（MyBatis）
│   │   │   ├── pojo/               # 实体类、VO、DTO
│   │   │   ├── ws/                 # WebSocket 服务端点
│   │   │   ├── utils/              # 工具类（JWT、图片处理）
│   │   │   ├── exception/          # 全局异常处理
│   │   │   └── interceptor/        # 拦截器（Token 校验）
│   │   ├── src/main/resources/
│   │   │   ├── application.yml     # 配置文件（需配置环境变量）
│   │   │   └── hal/th50743/mapper/ # MyBatis XML
│   │   └── pom.xml
│   └── dependencies/               # 自定义依赖
│       └── MinioOSSOperator/       # MinIO 集成 Starter
│
├── frontend/                       # 前端代码目录
│   └── vue-ezchat/                 # Vue 3 项目
│       ├── src/
│       │   ├── api/                # API 请求封装
│       │   ├── stores/             # Pinia 状态管理
│       │   ├── views/              # 页面组件
│       │   │   ├── index/          # 登录/注册页
│       │   │   ├── layout/         # 布局容器（侧边栏）
│       │   │   ├── chat/           # 聊天室主界面
│       │   │   ├── welcome/        # 欢迎页
│       │   │   └── error/          # 错误页
│       │   ├── components/         # 通用组件
│       │   ├── hooks/              # 组合式函数（Composables）
│       │   ├── WS/                 # WebSocket 客户端封装
│       │   ├── i18n/               # 国际化配置
│       │   ├── utils/              # 工具函数
│       │   ├── router/             # 路由配置
│       │   └── type/               # TypeScript 类型定义
│       ├── package.json
│       ├── vite.config.ts
│       └── CODE_REVIEW.md          # 代码审查报告
│
├── .cursorrules                    # AI 编码规范配置
├── .cursorignore                   # AI 忽略文件
├── .gitignore                      # Git 忽略配置
└── README.md                       # 本文件
```

---

## 🚀 快速启动

### 环境要求

- **Java**: 21 或更高版本
- **Node.js**: 20.19.0+ 或 22.12.0+
- **MySQL**: 5.7+ 或 8.0+
- **MinIO**: 用于对象存储（可选，或使用阿里云 OSS）

---

### 1️⃣ 后端启动（IntelliJ IDEA）

#### 步骤 1: 配置环境变量

在 IDEA 中配置以下环境变量（`Run > Edit Configurations > Environment Variables`）：

```bash
DB_URL=jdbc:mysql://localhost:3306/ezchat?useSSL=false&serverTimezone=Asia/Shanghai
DB_USERNAME=root
DB_PASSWORD=your_password

OSS_ENDPOINT=http://localhost:9000
OSS_ACCESS_KEY=minioadmin
OSS_SECRET_KEY=minioadmin
OSS_BUCKET_NAME=ezchat
OSS_PATH=/images

JWT_SECRET=your-256-bit-secret-key-here
JWT_EXPIRATION=86400000
```

#### 步骤 2: 初始化数据库

执行 SQL 脚本：
```bash
mysql -u root -p < backend/EZChat-app/src/main/resources/sql/init.sql
```

#### 步骤 3: 安装 MinIO 依赖

进入 `backend/dependencies/MinioOSSOperator` 目录，执行：
```bash
cd backend/dependencies/MinioOSSOperator
mvn clean install
```

#### 步骤 4: 启动后端

1. 在 IDEA 中打开项目根目录 `/home/dev/EZChat`
2. 右键 `backend/EZChat-app/src/main/java/hal/th50743/EzChatAppApplication.java`
3. 点击 `Run 'EzChatAppApplication'`
4. 后端将在 `http://localhost:8080` 启动

---

### 2️⃣ 前端启动（Vite Dev Server）

#### 步骤 1: 安装依赖

```bash
cd frontend/vue-ezchat
npm install
```

#### 步骤 2: 启动开发服务器

```bash
npm run dev
```

前端将在 `http://localhost:5173` 启动，Vite 已配置代理规则：
- `/api` → `http://localhost:8080`
- `/websocket` → `ws://localhost:8080/websocket`

#### 步骤 3: 访问应用

浏览器打开 `http://localhost:5173` 即可使用。

---

### 3️⃣ 生产构建

```bash
# 后端打包
cd backend/EZChat-app
mvn clean package

# 前端打包
cd frontend/vue-ezchat
npm run build
```

---

## 💡 开发协作规范

### 双刀流模式

本项目采用 **"IntelliJ IDEA 运行程序 + Cursor AI 编写代码"** 的双 IDE 协作模式：

1. **IDEA 职责**: 后端运行、调试、数据库管理
2. **Cursor 职责**: 代码编写、重构、AI 辅助

### 🔄 文件同步

**⚠️ 重要**: 在 Cursor 中修改代码后，必须在 IDEA 中执行同步操作：

```
快捷键: Ctrl + Alt + Y (Windows/Linux)
或菜单: File > Synchronize
```

否则 IDEA 可能读取到旧版本文件导致运行异常。

---

## 🤖 AI 编码规范

项目根目录已配置 `.cursorrules` 文件，AI 助手将遵循以下规范：

### 思考与回复
- **思考语言**: 英语（逻辑深度优先）
- **回复语言**: 简体中文

### 代码规范
- **前端**: 强制使用 `<script setup lang="ts">` + Composition API
- **后端**: 严格遵循 Controller → Service → Mapper 三层架构
- **解耦原则**: Vue 组件超过 300 行或 Java 方法过于复杂时，AI 会主动建议重构

### 同步策略
- 修改 POJO/Entity 时，AI 会提醒同步更新前端 TypeScript 接口
- 每次代码变更后，AI 会提示在 IDEA 中执行 `Ctrl+Alt+Y` 同步

---

## 📖 核心功能说明

### 用户系统
- 正式用户注册/登录（JWT 认证）
- 游客模式（临时访问聊天室）
- 头像上传（自动生成缩略图）

### 聊天系统
- 创建聊天室（可设置密码、过期时间）
- 加入聊天室（邀请链接/聊天码）
- 实时消息推送（文本 + 图片）
- 在线状态同步
- 消息已读标记
- 表情选择器

### WebSocket 机制
- 心跳保活（30s 间隔）
- 自动重连（5s 延迟）
- 消息 ACK 确认
- 断线提醒

---

## 🔧 配置说明

### 前端配置

**API 代理** (`vite.config.ts`):
```typescript
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
    rewrite: (path) => path.replace(/^\/api/, ''),
  },
}
```

**WebSocket 端口** (`src/stores/configStore.ts`):
```typescript
const API_PORT = 8080  // 后端端口
```

### 后端配置

**跨域设置** (`WebConfig.java`):
```java
registry.addMapping("/**")
    .allowedOriginPatterns("*")
    .allowedMethods("*")
```

**文件上传限制** (`application.yml`):
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 100MB
```

---

## 🐛 常见问题

### 1. WebSocket 连接失败

**原因**: 后端未启动或端口不一致

**解决**:
```bash
# 检查后端是否运行
curl http://localhost:8080/api/health

# 检查 WebSocket 端口配置
# frontend/vue-ezchat/src/stores/configStore.ts
const API_PORT = 8080
```

### 2. 图片上传失败

**原因**: MinIO 未启动或配置错误

**解决**:
```bash
# 启动 MinIO（Docker）
docker run -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio server /data --console-address ":9001"

# 访问 MinIO 控制台: http://localhost:9001
# 创建 bucket: ezchat
```

### 3. 前端代理失效

**原因**: Vite 配置未生效

**解决**:
```bash
# 重启开发服务器
npm run dev

# 检查请求是否带 /api 前缀
# 例如: axios.get('/api/user/info')
```

### 4. IDEA 文件不同步

**原因**: Cursor 修改后未同步

**解决**:
```
在 IDEA 中按 Ctrl+Alt+Y
或手动: File > Synchronize
```

---

## 📝 开发进度

- [x] 用户认证系统
- [x] 聊天室管理
- [x] 实时消息推送
- [x] 图片消息支持
- [x] 国际化支持
- [x] 暗色模式
- [ ] 消息撤回
- [ ] 文件传输
- [ ] 语音/视频通话
- [ ] 消息搜索

---

## 📄 许可证

本项目采用 MIT 许可证，详见 [LICENSE](LICENSE) 文件。

---

## 👥 贡献者

感谢所有为本项目做出贡献的开发者！

---

## 📧 联系方式

- **项目地址**: [GitHub](https://github.com/oojka/EZChat)
- **问题反馈**: [Issues](https://github.com/oojka/EZChat/issues)

---

<div align="center">

**⭐ 如果觉得项目不错，欢迎 Star ⭐**

</div>

