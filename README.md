[简体中文](./README_CN.md) | [日本語](./README_JA.md)

# EZ Chat

A modern real-time chat system built with **Spring Boot 3 + Vue 3**: WebSocket messaging, friend system, direct messaging, guest/registered auth, presence, image upload + thumbnails, i18n, and dark mode.

---

## Features

- **Friend System**: Search UID to add friends, bi-directional confirmation, friend list & presence
- **Direct Messaging**: Friend-based 1v1 chat, auto-creates private rooms
- **Real-time**: WebSocket messaging with heartbeat & ACK
- **Auth**: Dual Token (Access/Refresh) + auto-refresh, supports registered/guest access
- **Auto-Cleanup**: Auto-cleanup of inactive guest accounts
- **Rooms**: create rooms (join with password/invite links/one-time links), join rooms via chatCode
- **User Settings**: Update profile (avatar/nickname/bio), change password (formal users only)
- **Message Ordering**: Precise message ordering & pagination based on Sequence ID
- **Presence**: online-offline presence broadcast
- **Presence Debounce**: 30s offline buffer to prevent status flickering
- **Audit Logging**: Comprehensive operation logging for security audit
- **Image upload**: uploads with conditional thumbnails, unified 10MB size limit
- **Default avatar generation**: auto-generates default avatars via DiceBear API (bottts-neutral for users, identicon for rooms) when no avatar uploaded
- **Image deduplication**: dual-hash strategy (frontend pre-calculation + backend normalized hash) prevents duplicate uploads
- **Image optimization**: client-side compression + server-side normalization
- **Refresh UX**: load chat list first on refresh; members & messages are lazy/parallel to reduce blank screen
- **i18n**: Full coverage (incl. system messages & error toasts)
- **Type Safety**: Runtime validation (custom type guards) + TS strict mode
- **Dark mode**: Element Plus dark theme vars

---

## Tech Stack

### Backend

- **Java**: 17
- **Spring Boot**: 3.3.4
- **WebSocket**: Jakarta WebSocket + `@ServerEndpoint`
- **MyBatis**: 3.0.3
- **PageHelper**: 2.1.0 (Integration with MyBatis)
- **MySQL**: 8.x
- **JWT**: `jjwt` 0.11.5
- **Spring Security Crypto**: 6.3.4 (BCrypt)
- **Cache**: Caffeine (Local Cache)
- **Object Storage**: MinIO (Custom starter: `minio-oss-spring-boot-starter` 0.0.5-SNAPSHOT)
- **Thumbnail**: Thumbnailator 0.4.20
- **AOP**: Spring AOP (Process Logging)

### Frontend

- **Vue**: 3.5.25
- **TypeScript**: 5.9.x
- **Vite**: 7.2.x
- **Pinia**: 3.0.x
- **Vue Router**: 4.6.x
- **Element Plus**: 2.12.x
- **Axios**: 1.13.x

---

## Quick Start

### Prerequisites

- **Backend**: JDK 17+, Maven 3.6+, MySQL 8.x, MinIO (required by current config)
- **Frontend**: Node.js `^20.19.0 || >=22.12.0`, npm 10+

### Database schema

Schema is defined in `backend/EZChat-app/src/main/resources/sql/init.sql` (MySQL 8 + utf8mb4).

> ⚠️ Important
> `init.sql` drops tables, truncates data inside stored procedure, and generates lots of test data. Do NOT use in production.

#### Tables

- `users`: all users (guest + formal)
- `formal_users`: credentials for formal users
- `chats`: chat rooms
- `chat_members`: membership + last_seen_at
- `chat_sequences`: **[NEW]** chat message sequence counter
- `chat_invites`: invite codes
- `messages`: messages
- `assets`: **[NEW]** core assets table (was `objects`)
- `friendships`: **[NEW]** bi-directional friendships
- `friend_requests`: **[NEW]** friend requests
- `operation_logs`: operation audit logs

#### Key fields (summary)

`friendships`

| Field | Meaning |
|---|---|
| `id` (PK) | relationship id |
| `user_id` | user id (me) |
| `friend_id` | friend id (them) |
| `alias` | alias/remark |

`chats` (Updated)

| Field | Meaning |
|---|---|
| `id` (PK) | internal chat id |
| `type` | **[NEW]** 0=Group, 1=Private |
| `chat_code` (UNIQUE) | public chat code |
| `max_members` | max members |

`assets` (was `objects`)

| Field | Meaning |
|---|---|
| `id` (PK) | asset id |
| `asset_name` | MinIO object name |
| `original_name` | original file name |
| `category` | category |
| `message_id` | linked message id |
| `raw_asset_hash` | raw file hash |
| `normalized_asset_hash` | normalized hash |

#### Test data generation

`init.sql` creates and calls `generate_test_data()`, which by default:

- inserts **30 seed assets** into `assets` table
- inserts **100** users
- inserts **100** formal user credentials
- creates **20** chats (referenced seed asset as cover)
- generates **50** messages per chat (20% image messages, referencing seeds)
- **all users join all chats**
- creates some friendships and pending requests for User 1
- generates **10 garbage assets** for GC testing

### 1) Initialize database

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ezchat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p ezchat < backend/EZChat-app/src/main/resources/sql/init.sql
```

### 2) Set environment variables

Backend `application.yml` uses `${ENV}` placeholders; you **must** provide env vars (no built-in `.env` loader).

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

> Tip
> If you start backend in IntelliJ IDEA, set the same env vars in Run Configuration.

### 3) Start backend

```bash
cd backend/EZChat-parent
mvn -q -pl ../EZChat-app spring-boot:run
```

Default port: **8080**

### 4) Start frontend

```bash
cd frontend/vue-ezchat
npm install
npm run dev
```

Open: `http://localhost:5173`

---

## Business Logic

### Friend System (NEW)

#### Request & Confirmation
- **Bi-directional confirmation**: A sends request -> B accepts -> Friends.
- **Status flow**: `0=Pending`, `1=Accepted`, `2=Rejected`.
- **Constraints**: No duplicate requests; cannot add self; cannot add existing friends.

#### Private Chat
- **Friend-based**: Only friends can start private chat.
- **Auto-creation**: System checks for existing private room (Type=1). If none, creates one (Type=1, MaxMembers=2).
- **Reuse**: Reuses existing private room if available.

### Authentication & User Management

#### User Types

1. **Formal Users**:
   - Have `username` and `password_hash`
   - Login via `POST /auth/login`
   - Register via `POST /auth/register`
   - Shared `users` table
   - **Only formal users can use Friend System**.

2. **Guest Users**:
   - No `username`, only `nickname`
   - Created via `POST /auth/join` (password mode: `chatCode + password`, invite mode: `inviteCode`)
   - Can convert to formal via `POST /user/upgrade`

### Image Upload & Deduplication

#### Upload Flow

**Frontend**:
1. Calculate SHA-256 hash (`raw_asset_hash`)
2. Check existence via `GET /media/check`
3. If exists, reuse `assetId`
4. If not, compress and upload

**Backend**:
1. Normalize image (rotate, strip EXIF, convert to JPEG)
2. Calculate hash (`normalized_asset_hash`)
3. Check existence
4. If exists, reuse
5. If not, upload to MinIO and save to DB

### Object Association Design

Uses **logical foreign keys** to `assets` table:

**Fields**:
- **`users.asset_id`**
- **`chats.asset_id`**
- **`messages.asset_ids`** (JSON array)

**Optimization**:
- **Upload returns `assetId`**
- **Frontend sends `assetId`** directly

---

## Mobile Architecture (NEW)

The mobile authentication flow has been redesigned from a tab-based layout to a multi-page architecture.

### Routes

- `/m` - Welcome page with 3 entry cards
- `/m/guest` - Guest join form (Room ID/Password or Invite Link)
- `/m/login` - User login
- `/m/register` - User registration (2-step wizard)

### Core Components

Located in `frontend/vue-ezchat/src/views/mobile/entry/`:

- `MobileEntryShell.vue`: Shared layout component
- `MobileWelcomeView.vue`: Welcome page
- `MobileGuestJoinView.vue`: Guest join
- `MobileLoginView.vue`: Login
- `MobileRegisterView.vue`: Registration

### Design Highlights

- **Premium Glassmorphism**
- **Animated Background**: Floating orbs
- **Responsive Header**: Collapses on keyboard
- **Dark mode support**
- **Mobile-first touch targets**

---

## API Reference

### Friend System (`/friend/*`)

- **GET /friend/list**: Get friend list
- **GET /friend/requests**: Get pending requests
- **POST /friend/request**: Send friend request
- **POST /friend/handle**: Accept or reject request
- **POST /friend/chat**: Get or create private chat
- **DELETE /friend/{friendUid}**: Delete friend
- **PUT /friend/alias**: Update friend alias

---

## Changelog

### 2026-01-11 (Latest)
- **Features**:
  - **Friend System**: Implemented full friend lifecycle (Request, Accept, List, Remove).
  - **Private Chat**: Added support for 1v1 direct messaging with auto-creation logic.
  - **Mobile Auth**: Redesigned mobile auth layout (Tab-based → Multi-page `/m/*`) with glassmorphism design.
  - **UI Integration**: Added "Friends" tab in sidebar; New mobile entry components (`MobileEntryShell`, `MobileWelcomeView`, etc.).
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

---
*Last Updated: 2026-01-11*
