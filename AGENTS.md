# EZChat Agent Guidelines

This document provides essential information for AI agents working on the EZChat project.

## Project Overview

EZChat is a modern real-time chat system built with **Spring Boot 3 + Vue 3**. The codebase follows strict architectural patterns and type safety principles.

**Tech Stack:**
- **Backend**: Java 17, Spring Boot 3.3.4, MyBatis, WebSocket, MySQL, MinIO
- **Frontend**: Vue 3.5, TypeScript 5.9, Pinia, Vue Router, Element Plus, Vite

## Build, Lint & Test Commands

### Frontend (Vue 3 + TypeScript)

```bash
cd frontend/vue-ezchat

# Development
npm run dev                 # Start dev server (localhost:5173)

# Build
npm run build              # Type check + production build
npm run build-only         # Production build only (no type check)
npm run type-check         # TypeScript type checking only

# Code Quality
npm run lint               # ESLint with auto-fix
npm run format             # Prettier formatting

# Preview
npm run preview            # Preview production build
```

**Notes:**
- No dedicated test runner configured (Jest/Vitest). Use `npm run type-check` for type safety.
- ESLint configuration follows Vue 3 + TypeScript best practices.
- Prettier config: no semicolons, single quotes, 100-char print width.

### Backend (Spring Boot)

```bash
cd backend/EZChat-parent

# Development
mvn -q -pl ../EZChat-app spring-boot:run  # Run application (port 8080)

# Build & Test
mvn clean package          # Clean and package
mvn test                   # Run all tests
mvn compile               # Compile only

# For specific module (EZChat-app)
cd ../EZChat-app
mvn spring-boot:run       # Alternative run command
```

**Notes:**
- Standard Maven lifecycle commands apply.
- Test files are located in `src/test/java/`.
- No single-test command documented; use `mvn test -Dtest=ClassName`.

## Code Style Guidelines

### TypeScript / Vue 3

**Type Definitions:**
- Prefer `type` aliases over `interface` (except Vue component Props/Emits)
- All global types defined in `frontend/vue-ezchat/src/type/index.ts`
- API request types: `{FunctionName}Req` (e.g., `LoginApiReq`)
- Response types: `Promise<Result<T>>`

**Type Safety Protocol (STRICT):**
- **Zero `any` Policy**: Explicit `any` prohibited. Use `unknown` with type guards or generics.
- **No Lazy Assertions**: Avoid `as Type` casting. Use type guards or control flow narrowing.
- **Acceptable Exceptions**: DOM API assertions, experimental browser APIs, third-party library mismatches (must be documented).

**Vue Component Patterns:**
- Use `<script setup lang="ts">` exclusively
- Use `defineModel` for two-way binding
- Extract complex business logic to `composables/` (useXxx hooks)
- Keep components under 300 lines; suggest refactoring if exceeded

**API Function Patterns:**
- Parameter object pattern: All API functions accept single parameter object
- Example:
  ```typescript
  export type LoginApiReq = { username: string; password: string }
  export const loginApi = (data: LoginApiReq): Promise<Result<LoginUser>> =>
    request.post('/auth/login', data)
  ```

**Runtime Validation:**
- Use Zod or explicit type guards to validate backend responses
- Never blindly cast backend JSON responses

### Java / Spring Boot

**Architecture Layers (STRICT):**
- **Controller Layer**: HTTP handling only. No business logic. Inject Services only.
- **Service Layer**: Business logic, transactions (`@Transactional`), entity-to-DTO conversion.
- **Repository Layer**: Database access only (MyBatis mappers).

**Coding Standards:**
- Use Java 17+ features (Records, Text Blocks, Switch Expressions)
- Use Lombok (`@Data`, `@RequiredArgsConstructor`, `@Slf4j`)
- All REST APIs return `hal.th50743.pojo.Result<T>` (fully genericized)
- Logging: `@Slf4j` with INFO for business operations, ERROR before throwing exceptions
- Comments/Javadoc: **Chinese (Simplified)** required for ServiceImpl methods and Controller endpoints

**Error Handling:**
- Throw `BusinessException` for logic errors
- Global exception handler formats responses as `Result.error(code, message)`
- HTTP status 401 for auth failures (TokenInterceptor), other business errors return HTTP 200 with `status=0`

### Naming Conventions

- **TypeScript**: camelCase (variables, functions), PascalCase (types, classes, components)
- **Java**: camelCase (methods, variables), PascalCase (classes, interfaces)
- **Database**: snake_case (table and column names)

### Import Organization

- **Frontend**: Use `@/` alias for src directory (configured in vite.config.ts)
- **Backend**: Group imports: Java/Spring, third-party, project internal

### Error Handling Patterns

- **Frontend**: Use `Result<T>` wrapper, validate responses, display user-friendly error messages
- **Backend**: Consistent `Result<T>` with error codes (40xxx client, 41xxx user, 42xxx chat, 43xxx file, 50xxx server)

## Cursor Rules Integration

The project includes `.cursorrules` with critical instructions:

1. **Plan-First Protocol**: For code modifications, MUST:
   - Analyze & suggest alternatives
   - Draft execution plan in `.prompt.md` (English)
   - Wait for user "execute" command
   - Implement and log to `.cursor_dev_log`

2. **Output Language**: Respond in **Chinese (Simplified)** except for code identifiers and technical terminology.

3. **Thinking Process**: Always think in **English** for logical depth.

4. **Refactoring Threshold**: Suggest refactoring if Vue component >300 lines or Java method complexity high.

5. **Sync Reminder**: After backend changes, remind: "Please sync changes in IntelliJ IDEA (Ctrl+Alt+Y) before running."

## Project Structure

```
EZChat/
├── backend/
│   ├── EZChat-parent/              # Maven parent (aggregator)
│   ├── EZChat-app/                 # Spring Boot application
│   │   ├── src/main/java/hal/th50743/
│   │   │   ├── controller/         # REST controllers (HTTP only)
│   │   │   ├── service/            # Business logic
│   │   │   ├── mapper/             # MyBatis mappers
│   │   │   ├── ws/                 # WebSocket endpoint
│   │   │   └── utils/              # Utilities
│   │   └── src/main/resources/
│   │       ├── application.yml     # Config (env placeholders)
│   │       └── sql/init.sql        # DB initialization
│   └── dependencies/MinioOSSOperator/  # Custom MinIO starter
└── frontend/vue-ezchat/
    ├── src/
    │   ├── api/                    # API wrappers
    │   ├── composables/            # Vue composables (was hooks/)
    │   ├── stores/                 # Pinia stores
    │   ├── views/                  # Pages
    │   └── i18n/locales/           # zh/en/ja/ko/zh-tw
    └── vite.config.ts              # Dev proxy config
```

## Development Workflow

### Environment Setup
1. Database: Run `backend/EZChat-app/src/main/resources/sql/init.sql`
2. Environment variables: Set `DB_*`, `JWT_*`, `OSS_*` (no .env auto-load)
3. Backend: `mvn spring-boot:run` (port 8080)
4. Frontend: `npm run dev` (port 5173)

### Proxy Configuration
- Frontend dev proxy: `/api/*` → `http://localhost:8080/*`
- WebSocket proxy: `/websocket/*` → `ws://localhost:8080/websocket/*`
- **Note**: Backend routes do NOT include `/api` prefix

### Authentication
- HTTP Header: `token: <jwt>` (not `Authorization: Bearer`)
- JWT Claim: Use `uid` (lowercase)
- WebSocket endpoint: `/websocket/{token}`

## Important Conventions

1. **Type Safety**: Runtime validation (Zod) + TypeScript strict mode
2. **Internationalization**: Full i18n support (zh/en/ja/ko/zh-tw) including system messages
3. **Image Processing**: Frontend pre-compression + backend normalization
4. **Image Deduplication**: Dual-hash strategy (raw + normalized)
5. **Default Avatars**: DiceBear API (bottts-neutral for users, identicon for rooms)
6. **Object Association**: Logical foreign keys to `assets` table (not `assetName` queries)

## Agent Behavior Expectations

- Follow the plan-first protocol for non-trivial changes
- Maintain strict layer separation (Controller/Service/Repository)
- Enforce type safety and zero `any` policy
- Write Chinese comments for backend code
- Log changes to `.cursor_dev_log`
- Keep components and methods focused and maintainable

---

*Last Updated: 2026-01-11*  
*For detailed API documentation, see README.md*