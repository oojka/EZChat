# EZChat-app 后端知识库

**Generated:** 2026-01-13
**Commit:** 9bde4da
**Branch:** master

## OVERVIEW

Spring Boot 3.3.4 聊天后端：WebSocket 实时消息 + REST API + MyBatis + MySQL + MinIO 图片存储 + Caffeine 缓存。

## STRUCTURE

```
EZChat-app/
├── src/main/java/hal/th50743/
│   ├── controller/     # REST 端点 (薄层，仅 HTTP 处理)
│   ├── service/        # 业务逻辑 ★
│   │   └── impl/       # Service 实现
│   ├── mapper/         # MyBatis DAO + XML 映射
│   ├── pojo/           # DTO/VO/Entity (55 个文件)
│   ├── ws/             # WebSocket 服务端 (822 行核心)
│   ├── assembler/      # Entity → VO 转换器
│   ├── exception/      # ErrorCode + GlobalExceptionHandler
│   ├── config/         # Spring 配置类
│   ├── utils/          # 工具类
│   └── interceptor/    # Token 拦截器
├── src/main/resources/
│   ├── hal/th50743/mapper/*.xml   # MyBatis SQL 映射
│   ├── sql/init.sql               # 数据库初始化脚本
│   └── application.yml            # 配置 (全部 ${ENV} 占位符)
└── pom.xml
```

## WHERE TO LOOK

| 任务 | 位置 | 说明 |
|------|------|------|
| 新增 API | `controller/` + `service/impl/` | Controller 薄层，逻辑在 Service |
| 业务逻辑 | `service/impl/*Impl.java` | 所有业务逻辑集中于此 |
| 数据库查询 | `mapper/*.java` + `resources/.../mapper/*.xml` | 接口 + XML 分离 |
| WebSocket | `ws/WebSocketServer.java` | 822 行，消息广播/心跳/ACK |
| 认证 | `AuthServiceImpl.java` | 双 Token 机制 |
| 图片处理 | `AssetServiceImpl.java` | 去重/规范化/缩略图 |

## COMMANDS

```bash
# 运行 (需先设置环境变量 DB_*, JWT_*, OSS_*)
cd backend/EZChat-parent
mvn -q -pl ../EZChat-app spring-boot:run

# 测试
mvn test

# 仅编译
mvn compile -DskipTests
```

## CONVENTIONS

### 分层规则

| 层 | 可调用 | 禁止调用 |
|---|---|---|
| Controller | Service, Assembler | Mapper, WebSocket |
| Service | Mapper, Service, Assembler | Controller |
| Mapper | - | Service, Controller |

### 类注解模板

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class XxxServiceImpl implements XxxService {
```

### 事务

```java
@Transactional(rollbackFor = Exception.class)  // 必须指定 rollbackFor
```

### 错误处理

```java
throw new BusinessException(ErrorCode.USER_NOT_FOUND);
// 不要 catch BusinessException，让 GlobalExceptionHandler 统一处理
```

### 文档语言

- **Javadoc**: 中文 (简体)
- **日志**: 英文

## ERROR CODES

| 范围 | 领域 |
|------|------|
| 200 | 成功 |
| 40xxx | 客户端错误 |
| 41xxx | 用户相关 |
| 42xxx | 聊天/消息 |
| 43xxx | 文件相关 |
| 50xxx | 服务器错误 |

## ANTI-PATTERNS

- ❌ Controller 中写业务逻辑
- ❌ `@Transactional` 标注在接口上
- ❌ 捕获 `BusinessException`
- ❌ Service 返回 Entity 给 Controller (应用 Assembler 转 VO)
- ❌ Mapper 方法中硬编码 SQL (应写在 XML)

## ENVIRONMENT

必须设置的环境变量：
```
DB_URL, DB_USERNAME, DB_PASSWORD
JWT_SECRET
OSS_ENDPOINT, OSS_ACCESS_KEY, OSS_SECRET_KEY, OSS_BUCKET_NAME, OSS_PATH
```

## SUBDIRECTORY DOCS

- `src/.../pojo/AGENTS.md` - DTO/VO/Entity 规范
- `src/.../service/AGENTS.md` - Service 层规范
- `src/.../service/impl/AGENTS.md` - 实现类详情
- `src/.../mapper/AGENTS.md` - MyBatis 规范
- `src/.../controller/AGENTS.md` - REST API 规范
- `src/.../ws/AGENTS.md` - WebSocket 详情
