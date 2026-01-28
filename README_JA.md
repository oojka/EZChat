[English](./README.md) | [简体中文](./README_CN.md)

# EZ Chat

**Spring Boot 3 + Vue 3** で構築されたモダンなリアルタイムチャットシステム：WebSocket メッセージング、フレンドシステム、ダイレクトメッセージ、ゲスト/登録ユーザー認証、プレゼンス（オンライン状態）、画像アップロードとサムネイル、国際化（i18n）、ダークモードをサポート。

---

## 機能 / Features

- **フレンドシステム**: UID検索によるフレンド追加、双方向確認メカニズム、フレンドリストとオンライン状態表示
- **ダイレクトメッセージ**: フレンドベースの1対1チャット、プライベートルームの自動作成
- **リアルタイム**: WebSocket 双方向通信（メッセージブロードキャスト、ハートビート、ACK）
- **認証**: デュアルトークン（Access/Refresh）+ 自動更新、登録ユーザー/ゲスト参加をサポート
- **自動クリーンアップ**: 長期間オフラインのゲストアカウントとそのデータを自動削除
- **チャットルーム**: ルーム作成（パスワード/招待リンク/ワンタイムリンクで参加）、chatCodeによるルーム情報取得と参加
- **ユーザー設定**: プロフィール更新（アバター/ニックネーム/自己紹介）、パスワード変更（正式ユーザーのみ）
- **メッセージ順序**: Sequence IDに基づく正確なメッセージ順序とページネーション
- **プレゼンス**: オンライン/オフラインのブロードキャスト
- **プレゼンスデバウンス**: ネットワークの変動による誤検知を防ぐための30秒間のオフラインバッファ
- **操作監査**: セキュリティ監査のための包括的な操作ログ記録
- **画像アップロード**: 条件付きサムネイル生成付きアップロード、統一された10MBのサイズ制限
- **デフォルトアバター生成**: アバターがアップロードされていない場合、DiceBear APIを使用してデフォルトアバターを自動生成（ユーザーはbottts-neutral、ルームはidenticon）
- **画像重複排除**: 重複アップロードを防ぎストレージを節約するデュアルハッシュ戦略（フロントエンドの事前計算 + バックエンドの正規化ハッシュ）
- **画像最適化**: クライアント側の圧縮（アップロード体験の向上）+ サーバー側の正規化（互換性/プライバシー）
- **リフレッシュUX**: リフレッシュ時にチャットリストを優先的にロード、メンバーとメッセージは遅延/並列ロードして空白画面と待機時間を短縮
- **国際化 / i18n**: `zh/en/ja/ko/zh-tw` をフルカバー（システムメッセージとエラー通知を含む）
- **型安全性**: Zod ランタイム検証 + TypeScript ストリクトモード
- **ダークモード**: Element Plus ダークテーマ変数

---

## 技術スタック / Tech Stack

### バックエンド / Backend

- **Java**: 17
- **Spring Boot**: 3.3.4
- **WebSocket**: Jakarta WebSocket + `@ServerEndpoint`
- **MyBatis**: 3.0.3
- **PageHelper**: 2.1.0 (MyBatisとの統合)
- **MySQL**: 8.x
- **JWT**: `jjwt` 0.11.5
- **Spring Security Crypto**: 6.3.4 (BCrypt)
- **Cache**: Caffeine (ローカルキャッシュ)
- **Object Storage**: MinIO（自社製スターター：`minio-oss-spring-boot-starter` 0.0.5-SNAPSHOT）
- **Thumbnail**: Thumbnailator 0.4.20
- **AOP**: Spring AOP (プロセスロギング)

### フロントエンド / Frontend

- **Vue**: 3.5.25
- **TypeScript**: 5.9.x
- **Vite**: 7.2.x
- **Pinia**: 3.0.x
- **Vue Router**: 4.6.x
- **Element Plus**: 2.12.x
- **Axios**: 1.13.x

---

## クイックスタート / Quick Start

### 前提条件 / Prerequisites

- **Backend**: JDK 17+, Maven 3.6+, MySQL 8.x, MinIO (現在の設定で必須)
- **Frontend**: Node.js `^20.19.0 || >=22.12.0`, npm 10+

### データベーススキーマ / Database schema

スキーマは `backend/EZChat-app/src/main/resources/sql/init.sql` (MySQL 8 + utf8mb4) で定義されています。

> ⚠️ 重要
> `init.sql` はテーブルをドロップし、ストアドプロシージャ内でデータを切り捨て、大量のテストデータを生成します。本番環境では使用しないでください。

#### テーブル一覧 / Tables

- `users`: 全ユーザー（ゲスト + 正式ユーザー）、`user_type` を含む
- `formal_users`: 正式ユーザーの認証情報（ユーザー名/パスワードハッシュ）
- `chats`: チャットルーム（グループ/個人チャット統一）
- `chat_members`: メンバーシップ + 最終閲覧時間
- `chat_sequences`: **[NEW]** チャットメッセージシーケンスカウンター（現在の最大 seq_id）
- `chat_invites`: 招待コード（短縮リンク参加権限、TTL / 回数 / 取り消しを含む）
- `messages`: メッセージ本体（`seq_id` + `asset_ids` を含む）
- `assets`: **[NEW]** コア資産テーブル（旧 `objects`）、画像/ファイルメタデータを保存（MinIO）
- `friendships`: **[NEW]** フレンド関係テーブル（双方向記録）
- `friend_requests`: **[NEW]** フレンド申請テーブル（ステータス：Pending/Accepted/Rejected）
- `operation_logs`: 操作監査ログ

#### 主なフィールド（概要） / Key fields (summary)

`friendships`

| フィールド | 意味 |
|---|---|
| `id` (PK) | 関係ID |
| `user_id` | ユーザーID (自分) |
| `friend_id` | フレンドID (相手) |
| `alias` | 備考名 |

`chats` (Updated)

| フィールド | 意味 |
|---|---|
| `id` (PK) | 内部チャットID |
| `type` | **[NEW]** 0=グループ(Group), 1=プライベート(Private) |
| `chat_code` (UNIQUE) | 公開チャットコード |
| `max_members` | メンバー上限 (デフォルト 200, プライベートは 2) |

`assets` (was `objects`)

| フィールド | 意味 |
|---|---|
| `id` (PK) | 資産ID |
| `asset_name` | MinIO オブジェクト名 (旧 object_name) |
| `original_name` | 元のファイル名 |
| `category` | カテゴリ (USER_AVATAR/CHAT_COVER...) |
| `message_id` | 関連メッセージID (再利用モードでは空の場合あり) |
| `raw_asset_hash` | 元ファイルのハッシュ (SHA-256) |
| `normalized_asset_hash` | 正規化後のハッシュ (SHA-256) |

#### テストデータ生成 / Test data generation

`init.sql` はストアドプロシージャ `generate_test_data()` を作成して呼び出します。デフォルトでは以下の処理を行います：

- **30個のシードデータ**（実際の画像、ID 1-30）を `assets` テーブルに挿入
- **100人**のユーザーを挿入（各ユーザーはランダムにシードアバターを選択）
- **100個**の正式ユーザー認証情報を挿入
- **20個**のチャットルームを作成（各ルームはシードデータをカバーとして参照）
- 各ルームに **50件** のメッセージを生成（20%は画像メッセージ、シードを参照）
- **全ユーザーが全チャットルームに参加**
- **一部のフレンド関係を作成**：User 1 と User 2/3 をフレンドにし、いくつかの保留中の申請を生成
- **10個のゴミデータ**を生成（PENDING 状態、48時間前に作成、GCテスト用）

### 1) データベースの初期化 / Initialize database

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ezchat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p ezchat < backend/EZChat-app/src/main/resources/sql/init.sql
```

### 2) 環境変数の設定 / Set environment variables

バックエンド `backend/EZChat-app/src/main/resources/application.yml` はすべて `${ENV}` プレースホルダーを使用しています。**必ず**環境変数を注入してください（本プロジェクトには `.env` 自動ロード機能はありません）。

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

> ヒント
> IntelliJ IDEA でバックエンドを起動する場合は、Run Configuration で同じ環境変数を設定してください。

### 3) バックエンドの起動 / Start backend

```bash
cd backend/EZChat-parent
mvn -q -pl ../EZChat-app spring-boot:run
```

デフォルトポート: **8080**

### 4) フロントエンドの起動 / Start frontend

```bash
cd frontend/vue-ezchat
npm install
npm run dev
```

アクセス: `http://localhost:5173`

---

## ビジネスロジック / Business Logic

### フレンドシステム (NEW)

#### 申請と確認
- **双方向確認制**: A が B に申請 -> B が同意 -> フレンド成立。
- **ステータス遷移**: `0=Pending` (保留中), `1=Accepted` (同意済み), `2=Rejected` (拒否済み)。
- **制約**: 重複申請不可、自分自身の追加不可、既にフレンドのユーザー追加不可。

#### プライベートチャット
- **フレンドベース**: フレンド同士のみがプライベートチャットを開始できます。
- **自動作成**: フレンドをクリックしてチャットを開始する際、システムは共通のプライベートルーム（Type=1）が存在するか確認します。存在しない場合、双方を含むプライベートルーム（Type=1, MaxMembers=2）を自動作成します。
- **再利用**: 既に二人の間にプライベートルームが存在する場合、重複作成せずに再利用します。

### 認証とユーザー管理 / Authentication & User Management

#### ユーザータイプ

システムは2種類のユーザータイプをサポートしています：

1. **正式ユーザー / Formal Users**:
   - `username` と `password_hash` を持つ（`formal_users` テーブルに保存）
   - `POST /auth/login` でログイン可能
   - `POST /auth/register` で登録可能（アバターアップロード対応）
   - 全ユーザーが `users` テーブルを共有（`uid` を対外的な識別子として統一使用）
   - **正式ユーザーのみがフレンドシステムを使用可能**。

2. **ゲストユーザー / Guest Users**:
   - `username` なし、`nickname` のみ記録
   - `POST /auth/guest`（ルームID + パスワード）または `POST /auth/invite`（招待コード）で作成
   - 後に `POST /auth/register` で正式ユーザーに移行可能（`userUid` パラメータを提供）

### 画像アップロードと重複排除メカニズム / Image Upload & Deduplication

#### アップロードフロー

**フロントエンド前処理**:
1. Web Crypto API を使用して元ファイルの SHA-256 ハッシュ（`raw_object_hash`）を計算
2. `GET /media/check?rawHash=...` を呼び出して存在確認
3. 既に存在する場合、既存のオブジェクトを再利用（`objectId` を返す）し、アップロードをスキップ
4. 存在しない場合、フロントエンドで事前圧縮（`browser-image-compression`）を行い、アップロード

**バックエンド処理**:
1. アップロードファイルを受信し、正規化処理を実行：
   - **GIF ファイル**: 正規化処理を完全にスキップし、元ファイルを直接使用（アニメーション消失防止）
   - **その他の画像**:
     - 自動回転（EXIF Orientation）
     - メタデータ/EXIF削除（JPEG再エンコードによる）
     - 統一JPEG出力（品質約 0.85、最大辺 2048）
2. ハッシュ計算（重複排除比較用）：
   - **GIF ファイル**: 元ファイル内容の SHA-256 ハッシュを使用
   - **その他の画像**: 正規化後の内容の SHA-256 ハッシュ（`normalized_object_hash`）を使用
3. 同じハッシュのオブジェクトが存在するか照会（status=1）
4. 存在する場合、既存のオブジェクトを再利用（MinIOへの重複アップロードなし）
5. 存在しない場合、MinIOにアップロードし `objects` テーブルに書き込み（status=0, PENDING）

### オブジェクト関連設計 / Object Association Design

システムは `assets` テーブルとの関連付けに**論理外部キー**を統一して使用し、`assetName` によるクエリを回避してパフォーマンスを向上させています：

**関連フィールド**:
- **`users.asset_id`**: ユーザーアバターオブジェクト（`assets.id`）に関連付け
- **`chats.asset_id`**: チャットルームカバーオブジェクト（`assets.id`）に関連付け
- **`messages.asset_ids`**: メッセージ画像オブジェクトリスト（JSON 配列形式、例 `[1,2,3]`、`assets.id` リストを保存）に関連付け

**パフォーマンス最適化**:
- **アップロードインターフェースが `assetId` を返す**: すべての画像アップロードインターフェースは `assetId` フィールドを含む `Image` オブジェクトを返します
- **フロントエンドが `assetId` を直接渡す**: チャットルーム作成/メッセージ送信時、フロントエンドは `assetId` を直接渡し、バックエンドはテーブル参照不要

---

## モバイルアーキテクチャ (NEW)

モバイル認証フローは、よりスムーズで没入感のある体験を提供するために、タブベースのレイアウトからマルチページアーキテクチャに再設計されました。

### ルート構造 / Routes

- `/m` - ウェルカムページ (3つのエントリーカード)
- `/m/guest` - ゲスト参加 (ルーム番号+パスワード / 招待コード)
- `/m/login` - ユーザーログイン
- `/m/register` - ユーザー登録 (2ステップウィザード)

### コアコンポーネント / Core Components

`frontend/vue-ezchat/src/views/mobile/entry/` に配置：

- `MobileEntryShell.vue`: 共有レイアウトコンポーネント
- `MobileWelcomeView.vue`: ウェルカムページ
- `MobileGuestJoinView.vue`: ゲスト参加
- `MobileLoginView.vue`: ログイン
- `MobileRegisterView.vue`: 登録

### デザインのハイライト / Design Highlights

- **プレミアムグラスモーフィズム / Premium Glassmorphism**
- **動的背景 / Animated Background**: 浮遊する光球エフェクト
- **レスポンシブヘッダー / Responsive Header**: キーボード表示時に自動折りたたみ
- **ダークモード対応 / Dark mode support**
- **モバイルファーストタッチ / Mobile-first touch targets**

---

## API リファレンス / API Reference

### フレンドシステム / Friend System (`/friend/*`)

- **GET /friend/list**: フレンドリスト取得
- **GET /friend/requests**: 保留中のフレンド申請取得
- **POST /friend/request**: フレンド申請送信
- **POST /friend/handle**: フレンド申請処理（同意/拒否）
- **POST /friend/chat**: プライベートチャット取得または作成（chatCodeを返す）
- **DELETE /friend/{friendUid}**: フレンド削除
- **PUT /friend/alias**: フレンド備考修正

---

## 更新履歴 / Changelog

### 2026-01-11 (最新)
- **機能**:
  - **フレンドシステム**: 完全なフレンドライフサイクル（申請、同意、リスト、削除）を実装。
  - **プライベートチャット**: 自動作成ロジックを含む1対1のダイレクトメッセージのサポートを追加。
  - **モバイル認証**: モバイル認証レイアウトを再設計（タブベース → マルチページ `/m/*`）、グラスモーフィズムデザインを採用。
  - **UI統合**: サイドバーに「フレンド」タブを追加。新しいモバイルエントリーコンポーネント（`MobileEntryShell`, `MobileWelcomeView` など）を追加。
- **バックエンド**:
  - **スキーマ更新**: `friendships`, `friend_requests` テーブルを追加し、`chats` テーブルを更新。
  - **ロジック**: `FriendService` を実装し、プライベートチャット用に `ChatService` を拡張。

### 2026-01-10
- **機能 & UI**:
  - **ユーザー設定**: 包括的なユーザー設定ダイアログ（プロフィール更新、正式ユーザーのパスワード管理）を実装。
  - **プレミアムUI**: ドロップダウンのスタイル（単色背景、角丸）を洗練させ、メッセージエリアのローディングインジケーターを中央に配置。
  - **ゲスト体験**: ゲストのクリーンアップ閾値を2時間（以前は10分）に延長し、定着率を向上。
- **バックエンドリファクタリング**:
  - **ログ翻訳**: 保守性向上のため、すべてのバックエンドログを英語に完全国際化。
  - **API更新**: `PUT /user/password` を追加し、`POST /user/profile` エンドポイントを最適化。

### 2026-01-09
- **アーキテクチャ**:
  - **デュアルトークン認証**: キャッシュ（Caffeine）を使用したAccess/Refreshトークンメカニズムを実装。
  - **自動クリーンアップ**: 不活発なゲストアカウントを自動的に削除する `GuestCleanupService` を追加。
  - **操作監査**: すべてのCUD操作に対してAOPベースの操作ログ（`OperationLog` テーブル）を追加。
  - **SeqIdページネーション**: タイムスタンプベースのページネーションを安定した `seq_id` カーソルページネーションに置き換え。
  - **セキュリティ強化**: XSSリスクを軽減するため、localStorageでの `accessToken` 永続化を削除（メモリのみ）。

---
*最終更新: 2026-01-11*
