# EZChat - 轻量级实时聊天系统

<div align="center">

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen?style=flat-square&logo=springboot)
![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D?style=flat-square&logo=vue.js)
![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178C6?style=flat-square&logo=typescript)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

*一个基于 WebSocket 的现代化实时聊天应用，支持多房间、图片消息、多语言和暗黑模式*

</div>

---

## 📖 项目简介

EZChat 是一个功能完整的实时聊天系统，采用前后端分离架构。用户可以创建或加入聊天室，发送文本和图片消息，并实时查看在线状态。系统支持正式用户注册和访客模式，提供简洁优雅的用户体验。

### ✨ 核心特性

- 🚀 **实时通讯**：基于 WebSocket 实现毫秒级消息推送
- 🏠 **多房间管理**：支持创建/加入聊天室，密码保护可选
- 👥 **双重身份**：正式用户（JWT 认证）+ 访客模式（临时加入）
- 🖼️ **富媒体支持**：文本消息 + 图片上传（MinIO 对象存储）
- 🌍 **国际化**：内置中文、英文、日语、韩语、繁体中文支持
- 🌙 **暗黑模式**：自动适配系统主题偏好
- 📱 **响应式设计**：移动端友好的 UI 布局

---

## 🛠️ 技术栈

### 后端 (Spring Boot)

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 开发语言 |
| Spring Boot | 3.3.4 | 核心框架 |
| Spring WebSocket | 3.3.4 | WebSocket 支持 |
| MyBatis | 3.0.3 | 持久层框架 |
| MySQL | 8.x | 关系型数据库 |
| JWT (jjwt) | 0.11.5 | 身份认证 |
| MinIO | 自定义 Starter | 对象存储 (图片) |
| Thumbnailator | 0.4.20 | 图片缩略图生成 |
| Lombok | 1.18.34 | 简化 POJO 开发 |

### 前端 (Vue 3)

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5.25 | 前端框架 |
| TypeScript | 5.9 | 类型安全 |
| Vite | 7.2.4 | 构建工具 |
| Pinia | 3.0.4 | 状态管理 |
| Vue Router | 4.6.3 | 路由管理 |
| Element Plus | 2.12.0 | UI 组件库 |
| Vue I18n | 11.2.7 | 国际化 |
| Axios | 1.13.2 | HTTP 客户端 |
| ESLint + Prettier | - | 代码规范 |

---

## 📁 项目结构

```
EZChat/
├── backend/                          # 后端模块
│   ├── EZChat-parent/                # Maven 父工程
│   │   └── pom.xml                   # 统一依赖管理
│   ├── EZChat-app/                   # 主应用模块
│   │   ├── src/main/java/hal/th50743/
│   │   │   ├── config/               # 配置类 (WebSocket, CORS)
│   │   │   ├── controller/           # REST API 控制器
│   │   │   ├── service/              # 业务逻辑层
│   │   │   ├── mapper/               # MyBatis 数据访问层
│   │   │   ├── pojo/                 # 实体类/VO/DTO
│   │   │   ├── ws/                   # WebSocket 服务端
│   │   │   ├── utils/                # 工具类 (JWT, 图片处理)
│   │   │   ├── exception/            # 全局异常处理
│   │   │   └── interceptor/          # 拦截器 (Token 校验)
│   │   └── src/main/resources/
│   │       ├── application.yml       # 应用配置 (需配置环境变量)
│   │       └── hal/th50743/mapper/   # MyBatis XML 映射文件
│   └── dependencies/                 # 自定义依赖
│       └── MinioOSSOperator/         # MinIO 自动配置 Starter
│
├── frontend/                         # 前端模块
│   └── vue-ezchat/
│       ├── src/
│       │   ├── api/                  # API 请求封装
│       │   ├── components/           # 可复用组件
│       │   ├── views/                # 页面视图
│       │   │   ├── index/            # 登录/注册页
│       │   │   ├── layout/           # 主布局 (侧边栏)
│       │   │   ├── chat/             # 聊天室页面
│       │   │   ├── welcome/          # 欢迎页
│       │   │   └── error/            # 错误页
│       │   ├── stores/               # Pinia 状态管理
│       │   ├── router/               # 路由配置
│       │   ├── hooks/                # Vue Composables
│       │   ├── utils/                # 工具函数
│       │   ├── i18n/                 # 国际化翻译文件
│       │   ├── type/                 # TypeScript 类型定义
│       │   └── WS/                   # WebSocket 客户端
│       ├── public/                   # 静态资源
│       ├── vite.config.ts            # Vite 配置
│       └── package.json              # 依赖声明
│
├── .gitignore                        # Git 忽略规则
├── .cursorignore                     # Cursor AI 忽略规则
├── .cursorrules                      # Cursor AI 编码规范
└── README.md                         # 项目文档
```

---

## 🚀 快速开始

### 环境要求

| 工具 | 版本要求 |
|------|---------|
| JDK | 21+ |
| Maven | 3.6+ |
| Node.js | 20.19+ / 22.12+ |
| MySQL | 8.0+ |
| MinIO | 任意版本 (可选) |

### 1️⃣ 克隆项目

```bash
git clone https://github.com/oojka/EZChat.git
cd EZChat
```

### 2️⃣ 数据库初始化

1. 创建数据库：
```sql
CREATE DATABASE ezchat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 导入表结构（请联系项目维护者获取 SQL 文件）

### 3️⃣ 后端启动 (IntelliJ IDEA)

#### 配置环境变量

在 IDEA 的 **Run/Debug Configurations** 中设置以下环境变量：

```properties
# 数据库配置
DB_URL=jdbc:mysql://localhost:3306/ezchat?useSSL=false&serverTimezone=Asia/Tokyo
DB_USERNAME=root
DB_PASSWORD=your_password

# JWT 配置
JWT_SECRET=your_jwt_secret_key_at_least_256_bits
JWT_EXPIRATION=86400000

# MinIO 对象存储配置 (可选)
OSS_ENDPOINT=http://localhost:9000
OSS_ACCESS_KEY=minioadmin
OSS_SECRET_KEY=minioadmin
OSS_BUCKET_NAME=ezchat
OSS_PATH=/images
```

#### 启动步骤

1. 在 IDEA 中打开 `backend/EZChat-parent` 作为 Maven 项目
2. 等待 Maven 依赖下载完成
3. 找到 `backend/EZChat-app/src/main/java/hal/th50743/EzChatAppApplication.java`
4. 右键选择 **Run 'EzChatAppApplication'**
5. 确认控制台输出 `Started EzChatAppApplication` 表示启动成功

**默认端口：** `http://localhost:8080`

### 4️⃣ 前端启动 (Cursor / VS Code)

#### 安装依赖

```bash
cd frontend/vue-ezchat
npm install
```

#### 启动开发服务器

```bash
npm run dev
```

访问 `http://localhost:5173` 即可使用系统。

#### 其他命令

```bash
npm run build       # 生产构建
npm run type-check  # TypeScript 类型检查
npm run lint        # ESLint 代码检查
npm run format      # Prettier 格式化
```

---

## 🎯 开发协作规范

### 双 IDE 工作流 (Dual-IDE Workflow)

本项目采用 **"IntelliJ IDEA 运行程序 + Cursor AI 编写代码"** 的混合模式：

| IDE | 职责 |
|-----|------|
| **IntelliJ IDEA** | 后端 Spring Boot 程序运行、调试、数据库管理 |
| **Cursor AI** | 全栈代码编写、AI 辅助重构、前端开发 |

#### ⚠️ 重要：文件同步

当在 Cursor 中修改代码后，**必须**在 IDEA 中执行以下操作同步磁盘：

- **快捷键：** `Ctrl + Alt + Y` (Windows/Linux) / `Cmd + Option + Y` (macOS)
- **菜单路径：** File → Reload All from Disk

> 否则 IDEA 可能读取旧代码导致编译错误或运行异常！

### AI 编码规范 (.cursorrules)

项目根目录已配置 `.cursorrules` 文件，Cursor AI 会自动遵循以下原则：

- ✅ **思考语言：** 英语（保证逻辑深度）
- ✅ **回复语言：** 简体中文（提升沟通效率）
- ✅ **架构规范：**
  - 后端严格遵循 `Controller → Service → Mapper` 三层架构
  - 前端使用 `<script setup lang="ts">` + Composition API
  - Vue 组件超过 300 行时主动建议拆分
- ✅ **类型同步：** 修改后端 POJO 时同步更新前端 TS Interface

### Git 提交规范

```bash
# 格式：<type>(<scope>): <subject>

feat(chat): 添加聊天室消息撤回功能
fix(auth): 修复 JWT 过期时间计算错误
refactor(frontend): 优化 WebSocket 重连逻辑
docs(readme): 更新环境配置说明
chore(deps): 升级 Spring Boot 至 3.4.0
```

---

## 📡 核心功能模块

### 1. 用户认证

- **正式用户：** 用户名/密码注册 → JWT Token 认证
- **访客模式：** 临时昵称 + 房间密码快速加入

### 2. 聊天室管理

- 创建房间（自定义头像、名称、密码）
- 邀请链接生成（1-30天有效期）
- 房间列表展示（最后活跃时间排序）

### 3. 实时通讯

- **WebSocket 连接：** `/websocket/{token}`
- **心跳机制：** 客户端每 30 秒发送 `PING`
- **消息类型：**
  - `MESSAGE`：聊天消息
  - `USER_STATUS`：用户在线状态变更
  - `ACK`：消息送达确认

### 4. 图片消息

- 上传至 MinIO 对象存储
- 自动生成缩略图（Thumbnailator）
- 支持 JPEG/PNG 格式（< 10MB）

---

## 🌐 API 接口概览

### 认证相关

```http
POST /auth/register     # 用户注册
POST /auth/login        # 用户登录
POST /auth/guest        # 访客登录
```

### 聊天室相关

```http
GET  /chat/init         # 获取用户聊天列表
POST /chat/join         # 加入聊天室
GET  /chat/{chatCode}   # 获取聊天室详情
```

### 消息相关

```http
GET  /message/{chatCode}          # 获取聊天历史
GET  /message/{chatCode}/{time}   # 分页加载更早消息
```

### 用户相关

```http
GET  /user/{uid}        # 获取用户信息
POST /upload/image      # 上传图片
```

---

## 🔧 配置说明

### 后端配置 (application.yml)

所有敏感信息通过环境变量注入，避免硬编码：

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

jwt:
  secret: ${JWT_SECRET}           # 至少 256 位
  expiration: ${JWT_EXPIRATION}   # 毫秒（如 86400000 = 24小时）

minio:
  endpoint: ${OSS_ENDPOINT}
  accessKey: ${OSS_ACCESS_KEY}
  secretKey: ${OSS_SECRET_KEY}
  bucketName: ${OSS_BUCKET_NAME}
```

### 前端配置 (vite.config.ts)

开发环境自动代理后端 API：

```typescript
server: {
  host: '0.0.0.0',
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/api/, ''),
    },
  },
}
```

---

## 🐛 常见问题

### Q1: 后端启动报错 "Cannot resolve symbol 'MinioOSSOperator'"

**解决方案：** 先安装自定义 MinIO Starter

```bash
cd backend/dependencies/MinioOSSOperator
mvn clean install
```

### Q2: 前端运行 `npm install` 失败

**解决方案：** 清理缓存后重试

```bash
rm -rf node_modules package-lock.json
npm cache clean --force
npm install
```

### Q3: WebSocket 连接失败

**检查项：**
1. 后端是否正常运行（`http://localhost:8080/websocket/{token}` 可访问）
2. JWT Token 是否有效
3. 浏览器控制台是否有 CORS 错误

### Q4: 图片上传失败

**检查项：**
1. MinIO 服务是否启动
2. 环境变量 `OSS_ENDPOINT` 等配置是否正确
3. Bucket 是否已创建且设置为公共读取

---

## 📝 开发路线图

- [ ] 消息撤回功能
- [ ] 文件上传支持（PDF、Word）
- [ ] 语音消息
- [ ] 聊天室管理员权限
- [ ] 消息搜索
- [ ] 端到端加密
- [ ] 移动端 App (React Native)

---

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'feat: Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

---

## 📄 开源协议

本项目采用 [MIT License](LICENSE) 开源协议。

---

## 👨‍💻 作者

**oojka**

- GitHub: [@oojka](https://github.com/oojka)
- Email: kakoukaire@gmail.com

---

## 🙏 致谢

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Vue.js](https://vuejs.org/)
- [Element Plus](https://element-plus.org/)
- [MinIO](https://min.io/)
- [Cursor AI](https://cursor.sh/)

---

<div align="center">

**如果这个项目对你有帮助，请给一个 ⭐️ Star 支持一下！**

Made with ❤️ by oojka

</div>

