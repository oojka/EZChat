# EZ Chat - 现代化实时聊天系统

<div align="center">

![Version](https://img.shields.io/badge/version-1.0--SNAPSHOT-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3.5.25-42b883)
![License](https://img.shields.io/badge/license-MIT-green)

一个基于 **Spring Boot 3 + Vue 3** 构建的现代化实时聊天系统，支持多房间聊天、图片分享、在线状态同步等功能。

[功能特性](#-功能特性) • [技术栈](#-技术栈) • [快速开始](#-快速开始) • [开发指南](#-开发指南) • [项目结构](#-项目结构)

</div>

---

## 📋 功能特性

### 核心功能
- 🚀 **实时通信**：基于 WebSocket 的双向实时消息推送
- 💬 **多房间聊天**：支持创建、加入多个聊天室
- 🔐 **安全认证**：JWT Token 身份验证与会话管理
- 👥 **在线状态**：实时显示成员在线/离线状态
- 🖼️ **图片分享**：支持图片上传与自动缩略图生成
- 🌍 **国际化**：支持中文、英文、日语、韩语、繁体中文
- 🎨 **暗黑模式**：自适应系统主题，支持手动切换
- 📱 **响应式设计**：完美适配桌面与移动端

### 技术亮点
- ✨ **类型安全**：前后端全面 TypeScript/Java 类型守护
- 🔄 **状态管理**：Pinia 集中式状态管理
- 🎯 **分层架构**：Controller → Service → Mapper 清晰分层
- 🛡️ **错误处理**：统一异常拦截与友好提示
- ⚡ **性能优化**：Blob URL 管理、消息虚拟滚动、心跳保活

---

## 🛠 技术栈

### 后端技术
| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 主要开发语言 |
| Spring Boot | 3.3.4 | 应用框架 |
| Spring WebSocket | 3.3.4 | WebSocket 支持 |
| MyBatis | 3.0.3 | ORM 框架 |
| MySQL | 8.x | 关系型数据库 |
| JWT | 0.11.5 | Token 认证 |
| MinIO | 定制 | 对象存储（图片） |
| Thumbnailator | 0.4.20 | 图片缩略图生成 |
| Lombok | 1.18.34 | 代码简化 |

### 前端技术
| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5.25 | 渐进式框架 |
| TypeScript | 5.9.0 | 类型安全 |
| Vite | 7.2.4 | 构建工具 |
| Pinia | 3.0.4 | 状态管理 |
| Vue Router | 4.6.3 | 路由管理 |
| Element Plus | 2.12.0 | UI 组件库 |
| Vue I18n | 11.2.7 | 国际化 |
| Axios | 1.13.2 | HTTP 请求 |

---

## 🚀 快速开始

### 环境要求

#### 后端
- **JDK**: 21+
- **Maven**: 3.6+
- **MySQL**: 8.0+
- **MinIO**: 最新稳定版（可选，用于图片存储）

#### 前端
- **Node.js**: 20.19.0+ 或 22.12.0+
- **npm**: 10.0+

---

### 📦 安装步骤

#### 1️⃣ 克隆项目
```bash
git clone https://github.com/oojka/EZChat.git
cd EZChat
```

#### 2️⃣ 配置数据库
```sql
-- 创建数据库
CREATE DATABASE ezchat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 导入 SQL 文件（假设在项目根目录）
mysql -u root -p ezchat < database/schema.sql
```

#### 3️⃣ 配置后端环境变量
在 `backend/EZChat-app/src/main/resources/application.yml` 同级目录创建 `.env` 或配置环境变量：

```bash
# 数据库配置
DB_URL=jdbc:mysql://localhost:3306/ezchat?useSSL=false&serverTimezone=Asia/Tokyo
DB_USERNAME=root
DB_PASSWORD=your_password

# JWT 配置
JWT_SECRET=your_jwt_secret_key_at_least_256_bits
JWT_EXPIRATION=86400000

# MinIO 配置（可选）
OSS_ENDPOINT=http://localhost:9000
OSS_ACCESS_KEY=minioadmin
OSS_SECRET_KEY=minioadmin
OSS_BUCKET_NAME=ezchat
OSS_PATH=/images
```

#### 4️⃣ 启动后端（在 IntelliJ IDEA 中）
1. 用 IDEA 打开 `backend/EZChat-parent` 目录
2. 等待 Maven 依赖下载完成
3. 找到 `backend/EZChat-app/src/main/java/hal/th50743/EzChatAppApplication.java`
4. 右键 → **Run 'EzChatAppApplication'**
5. 看到 `Started EzChatAppApplication` 说明启动成功，默认端口 `8080`

#### 5️⃣ 启动前端（在 Cursor/Terminal 中）
```bash
cd frontend/vue-ezchat
npm install                  # 安装依赖
npm run dev                  # 启动开发服务器
```

访问 `http://localhost:5173` 即可使用。

---

## 💻 开发指南

### 🎯 双刀流开发模式

本项目采用 **"IDEA 运行 + Cursor AI 编码"** 的双 IDE 协作模式：

1. **IntelliJ IDEA**：专门用于运行后端 Spring Boot 项目
   - 优势：强大的 Java 调试、Maven 管理、数据库工具
   
2. **Cursor AI**：用于前后端代码编写与重构
   - 优势：AI 辅助编码、代码审查、快速重构

### ⚠️ 重要提醒

```diff
+ 在 Cursor 中修改代码后，切换到 IDEA 时务必执行：
+ 【Ctrl + Alt + Y】（Windows/Linux）或 【Cmd + Option + Y】（macOS）
+ 同步磁盘文件，避免 IDEA 使用过期缓存！
```

### 🤖 AI 编码规范

项目根目录已配置 `.cursorrules`，AI 助手将自动遵循以下规则：

- ✅ **思考语言**：英文思考（逻辑深度）
- ✅ **回复语言**：中文简体（便于沟通）
- ✅ **前端规范**：
  - 使用 `<script setup lang="ts">` 语法
  - 组件超过 300 行自动提示重构
  - 复杂逻辑提取到 `hooks/` 目录
- ✅ **后端规范**：
  - 严格遵循 Controller → Service → Mapper 分层
  - 使用 Java 21 特性（Records、Text Blocks）
  - 修改 POJO 时同步更新前端 TypeScript 接口

---

## 📁 项目结构

```
EZChat/
├── backend/                          # 后端项目
│   ├── EZChat-parent/                # Maven 父工程
│   │   └── pom.xml                   # 统一依赖管理
│   ├── EZChat-app/                   # 主应用模块
│   │   ├── src/main/java/hal/th50743/
│   │   │   ├── config/               # 配置类（WebSocket、CORS）
│   │   │   ├── controller/           # REST API 控制器
│   │   │   ├── service/              # 业务逻辑层
│   │   │   ├── mapper/               # MyBatis Mapper
│   │   │   ├── pojo/                 # 实体类与 VO
│   │   │   ├── ws/                   # WebSocket 服务端
│   │   │   ├── utils/                # 工具类（JWT、图片处理）
│   │   │   ├── exception/            # 异常处理
│   │   │   └── interceptor/          # 拦截器（Token 验证）
│   │   └── src/main/resources/
│   │       ├── application.yml       # 主配置文件
│   │       └── hal/th50743/mapper/   # MyBatis XML 映射
│   └── dependencies/                 # 自定义依赖（MinIO Starter）
│
├── frontend/                         # 前端项目
│   └── vue-ezchat/
│       ├── src/
│       │   ├── api/                  # API 请求封装
│       │   ├── assets/               # 静态资源
│       │   ├── components/           # 公共组件
│       │   │   └── dialogs/          # 对话框组件
│       │   ├── constants/            # 常量配置
│       │   ├── hooks/                # Vue Composables
│       │   ├── i18n/                 # 国际化配置
│       │   │   └── locales/          # 多语言文件
│       │   ├── router/               # 路由配置
│       │   ├── stores/               # Pinia 状态管理
│       │   ├── type/                 # TypeScript 类型定义
│       │   ├── utils/                # 工具函数
│       │   ├── views/                # 页面组件
│       │   │   ├── index/            # 登录注册页
│       │   │   ├── chat/             # 聊天主界面
│       │   │   ├── layout/           # 布局组件
│       │   │   ├── welcome/          # 欢迎页
│       │   │   └── error/            # 错误页
│       │   ├── WS/                   # WebSocket 客户端
│       │   ├── App.vue               # 根组件
│       │   └── main.ts               # 入口文件
│       ├── public/                   # 公共静态文件
│       ├── vite.config.ts            # Vite 配置
│       ├── tsconfig.json             # TypeScript 配置
│       └── package.json              # npm 依赖
│
├── .cursorrules                      # Cursor AI 编码规范
├── .cursorignore                     # Cursor 索引忽略
├── .gitignore                        # Git 忽略规则
└── README.md                         # 项目说明文档
```

---

## 📝 开发规范

### 前端开发规范
1. **组件命名**：使用 PascalCase（如 `ChatItem.vue`）
2. **文件组织**：按功能模块划分，避免单一文件过大
3. **类型安全**：所有 API 响应必须定义 TypeScript 接口
4. **状态管理**：全局状态使用 Pinia，局部状态使用 `ref`/`reactive`
5. **样式管理**：使用 scoped CSS，避免全局污染

### 后端开发规范
1. **分层原则**：Controller 仅负责参数校验与响应，业务逻辑放 Service
2. **异常处理**：使用 `@RestControllerAdvice` 统一处理异常
3. **数据验证**：使用 `@Valid` 注解 + 自定义校验器
4. **日志规范**：使用 Lombok 的 `@Slf4j`，生产环境关闭 DEBUG
5. **事务管理**：涉及多表操作必须加 `@Transactional`

---

## 🔧 常见问题

### 1. WebSocket 连接失败
- 检查后端是否启动（端口 8080）
- 确认 JWT Token 是否有效（F12 查看 Network 面板）
- 查看浏览器控制台是否有跨域错误

### 2. 图片上传失败
- 确认 MinIO 服务是否启动
- 检查 `application.yml` 中 MinIO 配置是否正确
- 查看后端日志是否有连接错误

### 3. 前端无法请求后端 API
- 检查 `vite.config.ts` 中的 proxy 配置
- 确认后端 CORS 配置已启用
- 查看浏览器 Network 面板的请求状态码

### 4. IDEA 中代码未更新
- 执行 `Ctrl+Alt+Y` 同步磁盘文件
- 清除 IDEA 缓存：`File → Invalidate Caches / Restart`

---

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建新分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

---

## 📄 许可证

本项目采用 MIT 许可证，详见 [LICENSE](LICENSE) 文件。

---

## 👨‍💻 作者

**oojka**
- GitHub: [@oojka](https://github.com/oojka)
- Email: kakoukaire@gmail.com

---

## 🙏 致谢

感谢以下开源项目：
- [Spring Boot](https://spring.io/projects/spring-boot)
- [Vue.js](https://vuejs.org/)
- [Element Plus](https://element-plus.org/)
- [MyBatis](https://mybatis.org/)
- [Vite](https://vitejs.dev/)

---

<div align="center">

**如果觉得项目不错，请给个 ⭐️ Star 支持一下！**

Made with ❤️ by oojka

</div>

