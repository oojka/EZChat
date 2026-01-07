/*
 Navicat MySQL Data Transfer
 Source Schema         : ezchat
 Target Server Type    : MySQL
 Target Server Version : 80000
 File Encoding         : 65001

 说明：
 1. 核心资产表 `assets` (原 objects) 仅包含 30 条真实 MinIO 种子数据。
 2. 用户头像、群封面、消息图片 **全部复用** 这 30 条数据的 ID (Ref Only)。
 3. 只有 Step 7 会生成 status=0 的垃圾数据用于测试 GC。
 4. 已更新 `chat_invites` 表结构，适应新的邀请码逻辑。
 5. 已完成 Object -> Asset 的全量重命名。
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================
-- 1. 建表语句 (DDL)
-- =============================================

-- 1.1 资产表 (核心，原 objects)
DROP TABLE IF EXISTS `assets`;
CREATE TABLE `assets` (
  `id`                     INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  `asset_name`             VARCHAR(255) NOT NULL COMMENT 'MinIO Object Key',
  `original_name`          VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `content_type`           VARCHAR(100) DEFAULT NULL,
  `file_size`              BIGINT UNSIGNED DEFAULT 0,
  `category`               VARCHAR(32) NOT NULL DEFAULT 'GENERAL' COMMENT '主要用途标记',
  `message_id`             INT UNSIGNED DEFAULT NULL COMMENT '关联消息ID (复用模式下此字段可能为空)',
  `status`                 TINYINT DEFAULT 0 COMMENT '0=PENDING (待清理), 1=ACTIVE (有效)',
  `raw_asset_hash`         CHAR(64) NULL COMMENT '原始文件Hash (SHA-256)',
  `normalized_asset_hash`  CHAR(64) NULL COMMENT '规范化Hash (后端计算)',
  `create_time`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  INDEX `idx_asset_name` (`asset_name`),
  INDEX `idx_gc_cleanup` (`status`, `create_time`),
  INDEX `idx_message_id` (`message_id`),
  INDEX `idx_normalized_hash` (`normalized_asset_hash`)
) COMMENT='数字资产表' CHARSET=utf8mb4;

-- (删除旧表防止混淆)
DROP TABLE IF EXISTS `objects`;
DROP TABLE IF EXISTS `files`;

-- 1.2 用户表 (关联 asset_id)
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id`           INT UNSIGNED AUTO_INCREMENT COMMENT '用户内部ID' PRIMARY KEY,
  `uid`          CHAR(10) NOT NULL COMMENT '对外ID',
  `nickname`     VARCHAR(20) NOT NULL,
  `asset_id`     INT UNSIGNED NULL COMMENT '头像ID (关联 assets.id)',
  `bio`          VARCHAR(255) NULL,
  `last_seen_at` DATETIME NOT NULL,
  `create_time`  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_time`  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT `uq_uid` UNIQUE (`uid`)
) COMMENT='用户基础信息表' CHARSET=utf8mb4;

-- 1.3 聊天室表 (关联 asset_id)
DROP TABLE IF EXISTS `chats`;
CREATE TABLE `chats` (
  `id`                 INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  `chat_code`          CHAR(8) NOT NULL COMMENT '对外ID',
  `chat_name`          VARCHAR(20) DEFAULT 'New Chat' NOT NULL,
  `owner_id`           INT UNSIGNED NULL COMMENT '群主ID',
  `chat_password_hash` VARCHAR(255) NULL,
  `join_enabled`       TINYINT DEFAULT 1 NOT NULL,
  `asset_id`           INT UNSIGNED NULL COMMENT '群封面ID (关联 assets.id)',
  `create_time`        DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_time`        DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT `uq_chat_code` UNIQUE (`chat_code`)
) COMMENT='聊天室/群组表' CHARSET=utf8mb4;

-- 1.4 消息表 (使用 asset_ids)
DROP TABLE IF EXISTS `messages`;
CREATE TABLE `messages` (
  `id`          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  `chat_id`     INT UNSIGNED NOT NULL,
  `sender_id`   INT UNSIGNED NOT NULL,
  `type`        TINYINT DEFAULT 0 NOT NULL COMMENT '0:Text, 1:Img, 2:Mixed, 10:System:room_created, 11:System:member_join, 12:System:member_leave, 13:System:owner_changed',
  `text`        LONGTEXT NULL,
  `asset_ids`   VARCHAR(4096) NULL COMMENT '图片ID列表 JSON数组 [1, 2]',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_chat_timeline` (`chat_id`, `create_time`)
) COMMENT='消息表' CHARSET=utf8mb4;

-- 1.5 其他辅助表
DROP TABLE IF EXISTS `chat_members`;
CREATE TABLE `chat_members` (
  `chat_id`      INT UNSIGNED NOT NULL,
  `user_id`      INT UNSIGNED NOT NULL,
  `last_seen_at` DATETIME NOT NULL,
  `create_time`  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_time`  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`chat_id`, `user_id`),
  INDEX `idx_user_chats` (`user_id`)
) COMMENT='群成员关联表' CHARSET=utf8mb4;

DROP TABLE IF EXISTS `chat_invites`;
CREATE TABLE `chat_invites` (
  `id`          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  `chat_id`     INT UNSIGNED NOT NULL COMMENT '关联chats表主键',
  `code_hash`   CHAR(64) NOT NULL,
  `expires_at`  DATETIME NOT NULL,
  `max_uses`    INT UNSIGNED DEFAULT 0 NOT NULL,
  `used_count`  INT UNSIGNED DEFAULT 0 NOT NULL,
  `revoked`     TINYINT DEFAULT 0 NOT NULL,
  `created_by`  INT UNSIGNED NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT `uq_invite_code` UNIQUE (`code_hash`),
  INDEX `idx_chat_id` (`chat_id`),
  INDEX `idx_expire` (`expires_at`)
) COMMENT='邀请码/短链接表' CHARSET=utf8mb4;

DROP TABLE IF EXISTS `formal_users`;
CREATE TABLE `formal_users` (
  `user_id`         INT UNSIGNED NOT NULL PRIMARY KEY,
  `username`        VARCHAR(50) NOT NULL,
  `password_hash`   VARCHAR(255) NOT NULL,
  `token`           VARCHAR(255) NULL,
  `last_login_time` DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `create_time`     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_time`     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT `uq_username` UNIQUE (`username`)
) COMMENT='正式用户认证表' CHARSET=utf8mb4;

-- =============================================
-- 1.6 业务审计日志表 (新增)
-- =============================================
DROP TABLE IF EXISTS `operation_logs`;
CREATE TABLE `operation_logs` (
  `id`             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  `user_id`        INT UNSIGNED NULL COMMENT '操作人ID (可能为空，如未登录尝试)',
  `module`         VARCHAR(50) NOT NULL COMMENT '功能模块 (如: Chat, User, System)',
  `type`           VARCHAR(50) NOT NULL COMMENT '操作类型 (如: KICK, LOGIN, UPDATE_PWD)',
  `content`        VARCHAR(1024) DEFAULT NULL COMMENT '详细描述 (支持SpEL解析后的内容)',
  `ip_address`     VARCHAR(50) DEFAULT NULL COMMENT '操作IP',
  `status`         TINYINT DEFAULT 1 COMMENT '1=成功, 0=失败',
  `execution_time` INT DEFAULT 0 COMMENT '耗时(毫秒)',
  `create_time`    DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
  
  INDEX `idx_user_time` (`user_id`, `create_time`),
  INDEX `idx_module_type` (`module`, `type`)
) COMMENT='业务操作审计日志表' CHARSET=utf8mb4;

-- =============================================
-- 2. 存储过程：生成全链路测试数据 (纯引用模式)
-- =============================================

DROP PROCEDURE IF EXISTS generate_test_data;

DELIMITER $$

CREATE PROCEDURE generate_test_data()
BEGIN
    -- 循环控制变量
    DECLARE i INT DEFAULT 1;
    DECLARE j INT DEFAULT 1;

    -- 临时数据变量
    DECLARE rand_user_id INT;
    DECLARE msg_type INT;
    DECLARE chat_owner INT;

    -- 游标与引用ID变量
    DECLARE v_seed_id INT;

    -- 常量
    DECLARE password_hash VARCHAR(255) DEFAULT '$2a$10$nIU8n7a0EgE37XlUM3zJJusqHg1VR9R9NJixvi35ILQmpq8yTPRn2';

    -- 性能优化
    SET FOREIGN_KEY_CHECKS = 0;
    SET UNIQUE_CHECKS = 0;
    SET AUTOCOMMIT = 0;

    -- 清空所有表
    TRUNCATE TABLE chat_invites;
    TRUNCATE TABLE messages;
    TRUNCATE TABLE chat_members;
    TRUNCATE TABLE chats;
    TRUNCATE TABLE formal_users;
    TRUNCATE TABLE users;
    TRUNCATE TABLE assets; 

    -- =============================================
    -- Step 1: 插入 30 条真实种子数据 (Seed Data, ID 1-30)
    -- =============================================
    INSERT INTO assets (id, asset_name, original_name, content_type, file_size, category, message_id, status, raw_asset_hash, normalized_asset_hash, create_time, update_time) VALUES
    (1, 'public/ezchat/04ae5084-c177-4523-a371-5387072b6cac/DE294B50DF84BFCA1F72E10FC8601576.jpg', 'DE294B50DF84BFCA1F72E10FC8601576.png', 'image/jpeg', 108566, 'USER_AVATAR', NULL, 1, NULL, 'ac605ab93e3954c1d08ad8babe5e7e3384a36707ef4cb17a830be43fb7662a2f', '2026-01-03 05:02:22', '2026-01-03 05:11:24'),
    (2, 'public/ezchat/c92dc483-668c-42c7-9d0d-177248fde52e/0207phn003.jpg', '0207phn003.jpg', 'image/jpeg', 388771, 'USER_AVATAR', NULL, 1, NULL, '97564c6f301658b99d9c1e1d1694827890b13a66a777d2947a114df04ca53297', '2026-01-03 05:17:30', '2026-01-03 05:18:32'),
    (3, 'public/ezchat/0016f8da-a289-4372-9288-fc50d9c375c5/4EF0713EDC3376AFBAB1451C62DE2D10.gif', '4EF0713EDC3376AFBAB1451C62DE2D10.gif', 'image/gif', 3193461, 'USER_AVATAR', NULL, 1, NULL, '266a6b09eb9533ce4136eb14fa3fa8ef63510d088be13e895d06f907bb72c72d', '2026-01-03 05:17:39', '2026-01-03 05:18:32'),
    (4, 'public/ezchat/31afaabd-d188-44ec-a97e-869e26264127/oUnANr7qQCxE1AF9mAMIPRCAAuALg6fCakWDEf_tplv-dy-aweme-images_q75.webp', 'oUnANr7qQCxE1AF9mAMIPRCAAuALg6fCakWDEf~tplv-dy-aweme-images_q75.webp', 'image/webp', 450924, 'USER_AVATAR', NULL, 1, NULL, '01964171e8e582a809828d022c24512845359b26b55aaa34222efc245315a0ef', '2026-01-03 05:17:47', '2026-01-03 05:18:32'),
    (5, 'public/ezchat/05f9bf23-1e9b-4455-94de-df04c6f55fb6/8A8B2353D2DC9AE9EBBCC5BCDA5C15A7.jpg', '8A8B2353D2DC9AE9EBBCC5BCDA5C15A7.jpg', 'image/jpeg', 38662, 'USER_AVATAR', NULL, 1, NULL, '1d89ea073e578f993248c748f6e406ed9235001d1fa7143bf017be13f05af3eb', '2026-01-03 05:20:04', '2026-01-03 05:22:56'),
    (6, 'public/ezchat/9e1422e7-8571-4540-97fa-c84d4cf0a475/691BA1F6-D199-F1DB-A105-887EE491A664.jpg', '{691BA1F6-D199-F1DB-A105-887EE491A664}.jpg', 'image/jpeg', 4110, 'USER_AVATAR', NULL, 1, NULL, 'd9b97c5e674bd3d1ea37905411273c49bc226a5654d65d90947412c8eac38c6d', '2026-01-03 05:20:14', '2026-01-03 05:22:56'),
    (7, 'public/ezchat/d5d2ef65-7713-49db-b371-645f4089c60c/4101005F-91DE-85D6-04E1-09E90592920A.jpg', '{4101005F-91DE-85D6-04E1-09E90592920A}.png', 'image/jpeg', 125277, 'USER_AVATAR', NULL, 1, NULL, 'cbfbf0f84f97b62c3229cae4d1a3d02bced81b4e4b554dc8793ef13fb5629483', '2026-01-03 05:20:19', '2026-01-03 05:22:56'),
    (8, 'public/ezchat/66e0a9a6-f9ec-46a6-ae30-ae8ad657fbf0/ED5E592B-29FD-84F8-3549-B6CFA2D04BAF.gif', '{ED5E592B-29FD-84F8-3549-B6CFA2D04BAF}.gif', 'image/gif', 935958, 'USER_AVATAR', NULL, 1, NULL, 'ec7422530ee87bc970424d0cbdce2671b6dba7929ec0efb98d0dbef5f4e5da2e', '2026-01-03 05:20:27', '2026-01-03 05:22:56'),
    (9, 'public/ezchat/62c883d4-b98b-46ba-9576-6b90a01c5990/39FB24749BCF42FFD81DAE3DCD36E8D9.jpg', '39FB24749BCF42FFD81DAE3DCD36E8D9.jpg', 'image/jpeg', 132717, 'USER_AVATAR', NULL, 1, NULL, '1d116f6682dd7ed4eade73279b2b87478c5b5b7fffc75f14e3d3786614eef2b6', '2026-01-03 05:20:33', '2026-01-03 05:22:56'),
    (10, 'public/ezchat/cf0c3a60-cfdb-49a5-907a-d3f08a480fa5/250FF6A3CBAECA868DAE99F2AC57AEF8.jpg', '250FF6A3CBAECA868DAE99F2AC57AEF8.png', 'image/jpeg', 664978, 'USER_AVATAR', NULL, 1, NULL, 'a01f3f8f6e31fe9a46e6a923d953e491775589e8a531ddc5e5f45a732ce0321b', '2026-01-03 05:20:40', '2026-01-03 05:22:56'),
    (11, 'public/ezchat/c2f4fd6f-b81d-43cb-9bdf-b0918835f801/771B709538E230DC45F108A41F4D6D06.jpg', '771B709538E230DC45F108A41F4D6D06.png', 'image/jpeg', 472438, 'USER_AVATAR', NULL, 1, NULL, 'c8ebd89232b4b23064be9130f359096e42fd195b7f2edb0ec21c36a36c5085fb', '2026-01-03 05:20:44', '2026-01-03 05:22:56'),
    (12, 'public/ezchat/b1cc1dcb-9da6-4b5e-b540-c63372833974/74718916A14E6B135BE660BA12379FDC.gif', '74718916A14E6B135BE660BA12379FDC.gif', 'image/gif', 702122, 'USER_AVATAR', NULL, 1, NULL, 'fe7f54f0affd48a64ea9778a3c4e70702e9253deadee45e7829e800450cd8732', '2026-01-03 05:20:50', '2026-01-03 05:22:56'),
    (13, 'public/ezchat/7d3a8cf8-4987-4e40-a0fe-b79e34ae671a/99036205D18CA5A7A27C8CB93A4F6C52.jpg', '99036205D18CA5A7A27C8CB93A4F6C52.jpg', 'image/jpeg', 114329, 'USER_AVATAR', NULL, 1, NULL, '8c189da536636e2be133193945d6db221278a48ede1be82ebb205b28e226e706', '2026-01-03 05:20:53', '2026-01-03 05:22:56'),
    (14, 'public/ezchat/4fda9920-72ed-4917-b21e-fb406b4421e3/79BC2C0B-CC9E-4CE7-684A-5078820C7B7E.jpg', '(79BC2C0B-CC9E-4CE7-684A-5078820C7B7E).jpg', 'image/jpeg', 8256, 'USER_AVATAR', NULL, 1, NULL, 'd571df60ef15ed3410722ce77146b1dac29c35346c165be50abdd83587b36c09', '2026-01-03 05:21:00', '2026-01-03 05:22:56'),
    (15, 'public/ezchat/854da858-2f73-4b22-93e3-7eb29675bfe0/01.jpg', '01.jpg', 'image/jpeg', 86762, 'USER_AVATAR', NULL, 1, NULL, '3051d6a778155615bd8cf9d2e4ccf9c3f69763b6d692ef05b1f476e8e660bd79', '2026-01-03 05:21:08', '2026-01-03 05:22:56'),
    (16, 'public/ezchat/90b1b805-264c-4359-83ab-101c47c53268/1.jpg', '1.jpg', 'image/jpeg', 8256, 'USER_AVATAR', NULL, 1, NULL, 'd571df60ef15ed3410722ce77146b1dac29c35346c165be50abdd83587b36c09', '2026-01-03 05:21:09', '2026-01-03 05:22:56'),
    (17, 'public/ezchat/b722073b-1c85-42d8-8a40-238fcc466d0c/02.jpg', '02.jpg', 'image/jpeg', 9371, 'USER_AVATAR', NULL, 1, NULL, '750decb5b3ab4e9a2c92600c1f4182c9279d81124968ed92f87b319b3ed7636f', '2026-01-03 05:21:11', '2026-01-03 05:22:56'),
    (18, 'public/ezchat/ee9233e6-a9ff-46a3-8f43-c6749f87e332/03.jpg', '03.jpg', 'image/jpeg', 25441, 'USER_AVATAR', NULL, 1, NULL, '4b3806f9bb827c417a45c70cfdd26f2ef97f3dde5c7ebfb35026fddc9bd6e8cc', '2026-01-03 05:21:14', '2026-01-03 05:22:56'),
    (19, 'public/ezchat/1276f52f-4d14-4a25-adb4-8e40e2f1beca/51F-jNF1frL._AC_UF894_1000_QL80.jpg', '51F-jNF1frL._AC_UF894,1000_QL80_.jpg', 'image/jpeg', 94504, 'USER_AVATAR', NULL, 1, NULL, '8349a2f66d1dfb1a260b92c798613c17821a03edcea03737d78a80d8df76e835', '2026-01-03 05:21:16', '2026-01-03 05:22:56'),
    (20, 'public/ezchat/10ad33f6-fb08-445e-a736-ac5e51fc5e22/80acbfed-ad9f-4327-8adc-66fff69dbe4f_e7fe6489-1e91-46ba-94b2-40136d6f8de9.jpg', '80acbfed-ad9f-4327-8adc-66fff69dbe4f_e7fe6489-1e91-46ba-94b2-40136d6f8de9.png', 'image/jpeg', 25920, 'USER_AVATAR', NULL, 1, NULL, '43418fe254b798b1e2bdf116cbb4315b0b58db3df4a8f65f794838d544a26504', '2026-01-03 05:21:18', '2026-01-03 05:22:56'),
    (21, 'public/ezchat/38221715-12d3-4c39-994f-949d393a8dd7/360_F_460031310_ObbCLA1tKrqjsHa7je6G6BSa7iAYBANP.jpg', '360_F_460031310_ObbCLA1tKrqjsHa7je6G6BSa7iAYBANP.jpg', 'image/jpeg', 14916, 'USER_AVATAR', NULL, 1, NULL, '92a327463665b9effea5604ce0661e59c60828ce8ba9bff3863a80f2a45552b3', '2026-01-03 05:21:20', '2026-01-03 05:22:56'),
    (22, 'public/ezchat/d285ad3a-a943-4601-9751-c9b5a5fd5b24/73024655.jpg', '73024655.jpg', 'image/jpeg', 49467, 'USER_AVATAR', NULL, 1, NULL, '5bf84c4343f29be83d213261ff481818cdfd42fcd7a9e09f349ffaf862f9ebbb', '2026-01-03 05:21:26', '2026-01-03 05:22:56'),
    (23, 'public/ezchat/062be252-4ae6-4c96-807b-f7790a3b3a58/Chouju_sumo2.jpg', 'Chouju_sumo2.jpg', 'image/jpeg', 62076, 'USER_AVATAR', NULL, 1, NULL, '264de28dc90e3c861abf4f704100538a77c8cbcec85a1aa942331fa5eee594eb', '2026-01-03 05:21:34', '2026-01-03 05:22:56'),
    (24, 'public/ezchat/8e1b67a9-618e-4de7-9c77-c3b765551f8d/73024655.jpg', '73024655.jpg', 'image/jpeg', 49467, 'USER_AVATAR', NULL, 1, NULL, '5bf84c4343f29be83d213261ff481818cdfd42fcd7a9e09f349ffaf862f9ebbb', '2026-01-03 05:21:38', '2026-01-03 05:22:56'),
    (25, 'public/ezchat/9e2941ef-ca4e-4d19-bad7-561e20f6e945/makabaka.jpg', 'makabaka.jpg', 'image/jpeg', 15815, 'USER_AVATAR', NULL, 1, NULL, '7779347e483b8ad91c8031b24cf8089c0cc228910e07e9685e3eb6415f65dcba', '2026-01-03 05:21:41', '2026-01-03 05:22:56'),
    (26, 'public/ezchat/200fd3c1-4115-4d8b-8229-c2853b775d60/gandam.jpg', 'gandam.jpg', 'image/jpeg', 12217, 'USER_AVATAR', NULL, 1, NULL, '4d75616ccb60c99ec55b4a97f683f4559142dbea87c081950b348ea3d159e81f', '2026-01-03 05:21:45', '2026-01-03 05:22:56'),
    (27, 'public/ezchat/504f8019-ccb0-4c48-929a-d91450d2e7c2/Chouju_sumo2.jpg', 'Chouju_sumo2.jpg', 'image/jpeg', 62076, 'USER_AVATAR', NULL, 1, NULL, '264de28dc90e3c861abf4f704100538a77c8cbcec85a1aa942331fa5eee594eb', '2026-01-03 05:21:49', '2026-01-03 05:22:56'),
    (28, 'public/ezchat/4c98a5c3-75a8-4ce8-9052-e7fd41994780/USA.jpg', 'USA.jpg', 'image/jpeg', 22122, 'USER_AVATAR', NULL, 1, NULL, '5a4760be93cab324b1e3a0fc7c11332e232feb271a396a9619a6c785e8e45fb0', '2026-01-03 05:21:54', '2026-01-03 05:22:56'),
    (29, 'public/ezchat/fab1bac7-e3db-455e-b728-49fa02d424a3/wenquan.jpg', 'wenquan.jpg', 'image/jpeg', 17393, 'USER_AVATAR', NULL, 1, NULL, '31a75818ce870793512952af285f6ec4621dd79e716e9178dc613b96d37c819e', '2026-01-03 05:21:58', '2026-01-03 05:23:11'),
    (30, 'public/ezchat/42d37661-3ece-4457-965d-cebfa35c6f24/file.jpg', '下载.jpg', 'image/jpeg', 14025, 'USER_AVATAR', NULL, 1, NULL, '64f524010b21448ee45c9aad0431cd1611d3fbf38db7a1bfe0126ca420845682', '2026-01-03 05:22:04', '2026-01-03 05:23:11');

    COMMIT;

    -- =============================================
    -- Step 2: 生成用户 (关联随机种子头像)
    -- =============================================
    SET i = 1;
    WHILE i <= 100 DO
        -- 随机选取一个种子对象 (ID 1-30)
        SET v_seed_id = FLOOR(1 + RAND() * 30);

        INSERT INTO users (id, uid, nickname, asset_id, bio, last_seen_at, create_time)
        VALUES (
           i,
           CONCAT('1000', LPAD(i, 6, '0')),
           CONCAT('User_', LPAD(i, 3, '0')),
           v_seed_id,
           'Hello World!',
           DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 1440) MINUTE),
           DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30) DAY)
        );
        SET i = i + 1;
    END WHILE;
    COMMIT;

    -- =============================================
    -- Step 3: 生成正式用户
    -- =============================================
    SET i = 1;
    WHILE i <= 100 DO
        INSERT INTO formal_users (user_id, username, password_hash)
        VALUES (i, CONCAT('test', LPAD(i, 3, '0')), password_hash);
        SET i = i + 1;
    END WHILE;
    COMMIT;

    -- =============================================
    -- Step 4: 生成聊天室 (直接引用种子ID，不克隆)
    -- =============================================
    SET i = 1;
    WHILE i <= 20 DO
        SET chat_owner = FLOOR(1 + RAND() * 100);

        -- 随机选取一个种子对象 (ID 1-30) 作为群封面
        SET v_seed_id = FLOOR(1 + RAND() * 30);

        INSERT INTO chats (id, chat_code, chat_name, owner_id, join_enabled, asset_id, create_time)
        VALUES (
           i,
           CAST(20000000 + i AS CHAR),
           CONCAT('Group ', i),
           chat_owner,
           1,
           v_seed_id, -- 直接引用，不新增 asset
           DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30) DAY)
        );
        SET i = i + 1;
    END WHILE;
    COMMIT;

    -- =============================================
    -- Step 5: 生成成员 (全员入全群)
    -- =============================================
    INSERT INTO chat_members (chat_id, user_id, last_seen_at)
    SELECT id, owner_id, NOW() FROM chats;

    SET i = 1;
    WHILE i <= 20 DO
        SET j = 1;
        WHILE j <= 100 DO
            INSERT IGNORE INTO chat_members (chat_id, user_id, last_seen_at)
            VALUES (i, j, NOW());
            SET j = j + 1;
        END WHILE;
        SET i = i + 1;
    END WHILE;
    COMMIT;

    -- =============================================
    -- Step 6: 生成消息 (Text + Image 引用)
    -- =============================================
    SET i = 1;
    WHILE i <= 20 DO
        SET j = 1;
        WHILE j <= 50 DO
            SELECT user_id INTO rand_user_id FROM chat_members WHERE chat_id = i ORDER BY RAND() LIMIT 1;

            -- 20% 概率发送图片
            SET msg_type = CASE WHEN RAND() < 0.2 THEN 1 ELSE 0 END;

            IF msg_type = 0 THEN
                INSERT INTO messages (sender_id, chat_id, type, text, create_time)
                VALUES (rand_user_id, i, 0, 'Hello, this is a test message.', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 1000) MINUTE));
            ELSE
                -- 随机选取一个种子对象 (ID 1-30) 作为消息图片
                SET v_seed_id = FLOOR(1 + RAND() * 30);

                -- 直接将该 ID 写入 JSON 数组，不新增 asset
                INSERT INTO messages (sender_id, chat_id, type, asset_ids, create_time)
                VALUES (rand_user_id, i, 1, CONCAT('[', v_seed_id, ']'), DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 1000) MINUTE));
            END IF;

            SET j = j + 1;
        END WHILE;
        SET i = i + 1;
    END WHILE;
    COMMIT;

    -- =============================================
    -- Step 7: 生成垃圾数据 (Pending GC Test)
    -- =============================================
    SET i = 1;
    WHILE i <= 1000 DO
        INSERT INTO assets (asset_name, original_name, content_type, category, status, create_time)
        VALUES (
           CONCAT('temp/orphan_', UUID(), '.jpg'),
           'deleted_soon.jpg',
           'image/jpeg',
           'GENERAL',
           0, -- Status 0 = PENDING
           DATE_SUB(NOW(), INTERVAL 48 HOUR) -- 48小时前，理应被GC
        );
        SET i = i + 1;
    END WHILE;
    COMMIT;

    -- 恢复环境
    SET FOREIGN_KEY_CHECKS = 1;
    SET UNIQUE_CHECKS = 1;
    SET AUTOCOMMIT = 1;

    SELECT 'Data Generation Complete (Asset Mode).' AS result;
    SELECT CONCAT('Total Assets: ', (SELECT COUNT(*) FROM assets)) AS Total_Assets;
    -- 修正此处的文本提示以匹配代码逻辑：30个种子 + 1000个垃圾 = 1030
    SELECT 'Assets count should be roughly 1030 (30 seeds + 1000 garbage).' AS Expected;
    SELECT CONCAT('Users Count: ', (SELECT COUNT(*) FROM users)) AS Users;
    SELECT CONCAT('Chats Count: ', (SELECT COUNT(*) FROM chats)) AS Chats;
    SELECT CONCAT('Messages Count: ', (SELECT COUNT(*) FROM messages)) AS Messages;

END$$

DELIMITER ;

-- 执行生成
CALL generate_test_data();