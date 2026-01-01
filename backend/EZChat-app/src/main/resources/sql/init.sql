/*
 Navicat MySQL Data Transfer
 Target Server Type    : MySQL
 Target Server Version : 80000
 File Encoding         : 65001

 说明：已将 users 表字段统一修改为 uid (原 u_id)，以匹配后端 Mapper XML 中的定义。
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================
-- 1. 建表语句 (DDL)
-- =============================================

DROP TABLE IF EXISTS `chat_members`;
create table chat_members
(
    chat_id      int unsigned                       not null comment '聊天内部ID，对应chats.id',
    user_id      int unsigned                       not null comment '成员用户内部ID，对应users.id',
    last_seen_at datetime                           not null comment '最后一次看该chat时间',
    create_time  datetime default CURRENT_TIMESTAMP not null comment '记录创建时间（建议按UTC存储）',
    update_time  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '记录最后修改时间（建议按UTC存储）',
    primary key (chat_id, user_id)
)
    comment '聊天成员表：记录每个聊天中的成员' charset = utf8mb4;

DROP TABLE IF EXISTS `chats`;
create table chats
(
    id                 int unsigned auto_increment comment '自增主键，聊天内部ID（chat_pk）'
        primary key,
    chat_code          char(8)                               not null comment '聊天对外ID（chatId），8位字符串',
    chat_name          varchar(20) default 'New Chat'        not null comment '聊天名称 / 群名称，可由成员修改，默认"New Chat"',
    owner_id           int unsigned                          null comment '群主用户内部ID，对应users.id，通常为第一次创建该聊天的用户',
    chat_password_hash varchar(255)                          null comment '聊天密码的哈希值',
    join_enabled       tinyint     default 1                 not null comment '是否允许通过群ID+密码加入：1=允许，0=禁止',
    create_time        datetime    default CURRENT_TIMESTAMP not null comment '聊天创建时间（建议按UTC存储）',
    update_time        datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '聊天信息最后修改时间（建议按UTC存储）',
    avatar_name        varchar(255)                          null,
    constraint chat_code
        unique (chat_code)
)
    comment '聊天表：统一存储单聊和群聊的基本信息' charset = utf8mb4;

DROP TABLE IF EXISTS `files`;
create table files
(
    id           int unsigned auto_increment comment '文件ID，自增主键'
        primary key,
    message_id   int unsigned                       not null comment '所属消息ID，对应messages.id',
    file_name    varchar(255)                       not null comment '原始文件名（用户看到的文件名）',
    content_type varchar(100)                       null comment '文件MIME类型，例如image/png、application/pdf等',
    file_path    varchar(255)                       not null comment '文件在服务器上的物理存储路径或相对路径',
    create_time  datetime default CURRENT_TIMESTAMP not null comment '文件上传时间（建议按UTC存储）',
    update_time  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '文件记录最后修改时间（建议按UTC存储）'
)
    comment '文件表：存储消息附带的图片/文件的元数据与存储路径' charset = utf8mb4;

DROP TABLE IF EXISTS `formal_users`;
create table formal_users
(
    user_id         int unsigned                       not null comment '用户内部ID，对应users.id，同时作为本表主键'
        primary key,
    username        varchar(50)                        not null comment '登录用户名，唯一',
    password_hash   varchar(255)                       not null comment '登录密码的哈希值（例如BCrypt）',
    create_time     datetime default CURRENT_TIMESTAMP not null comment '成为正式用户的时间（建议按UTC存储）',
    update_time     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '记录最后修改时间（建议按UTC存储）',
    last_login_time datetime default CURRENT_TIMESTAMP not null,
    constraint username
        unique (username)
)
    comment '正式用户表：只存有账号体系的用户信息（登录名与密码哈希）' charset = utf8mb4;

DROP TABLE IF EXISTS `messages`;
create table messages
(
    id           int unsigned auto_increment comment '自增主键，消息内部ID'
        primary key,
    sender_id    int unsigned                       not null comment '发送方用户内部ID，对应users.id',
    chat_id      int unsigned                       not null comment '所属聊天内部ID，对应chats.id',
    text         longtext                           null comment '消息内容（明文）；文本消息为文本内容，文件消息可存文件说明或预览文本',
    create_time  datetime default CURRENT_TIMESTAMP not null comment '消息创建（发送）时间（建议按UTC存储）',
    update_time  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '消息记录最后修改时间（建议按UTC存储）',
    object_names varchar(4096)                      null
)
    comment '消息表：存储各聊天中的聊天消息' charset = utf8mb4;

DROP TABLE IF EXISTS `users`;
create table users
(
    id            int unsigned auto_increment comment '自增主键，用户内部ID'
        primary key,
    uid           char(10)                           not null comment '用户对外ID，纯数字固定10位，唯一身份标识',
    nickname      varchar(20)                        not null comment '用户昵称，建议不超过20个字符',
    avatar_object varchar(255)                       null comment '头像图片的object name',
    bio           varchar(255)                       null comment '个人简介 / 个性签名',
    last_seen_at  datetime                           not null comment '最后在线时间，用于判断离线清理',
    create_time   datetime default CURRENT_TIMESTAMP not null comment '记录创建时间（建议按UTC存储）',
    update_time   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '记录最后修改时间（建议按UTC存储）',
    constraint user_id
        unique (uid)
)
    comment '用户表：包含所有用户（临时用户和正式用户）的基础信息' charset = utf8mb4;

-- =============================================
-- 2. 存储过程：生成多语言混合测试数据
-- =============================================

DROP PROCEDURE IF EXISTS generate_japanese_test_data;

DELIMITER $$

CREATE PROCEDURE generate_japanese_test_data()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE j INT DEFAULT 1;
    DECLARE msg_count INT DEFAULT 0;
    DECLARE rand_user_id INT;
    DECLARE rand_chat_id INT;
    DECLARE msg_type INT; -- 0=text, 1=image
    DECLARE avatar_idx INT;
    DECLARE image_idx INT;
    DECLARE chat_owner INT;

    -- 密码哈希值
    DECLARE password_hash VARCHAR(255) DEFAULT '$2a$10$//Cuez3MneKPnLPQICdrMuUJ3lZ8KQxFr.p.zOhNnkfG9IJAiYEr.';

    -- List A: Public Avatars
    DECLARE avatar_1 VARCHAR(255) DEFAULT 'public/ezchat/423c3d9a-6dfe-41b5-8778-841d424e7609/unnamed.jpg';
    DECLARE avatar_2 VARCHAR(255) DEFAULT 'public/ezchat/d7badc14-0968-43c9-ab06-1c255101e5f3/DE294B50DF84BFCA1F72E10FC8601576.png';
    DECLARE avatar_3 VARCHAR(255) DEFAULT 'public/ezchat/3151ed41-9f67-4e17-9db0-a0cb21cb5fe8/{6574DC50-2835-8ECB-A21A-669949DD7611}.jpg';
    DECLARE avatar_4 VARCHAR(255) DEFAULT 'public/ezchat/de91f422-f4e5-4997-be63-1d4eff2bf8e1/0ADAFC829F328DF2E7F93648486BBB6E.jpg';
    DECLARE avatar_5 VARCHAR(255) DEFAULT 'public/ezchat/addfa91a-4e38-477f-a7f1-0d9a158cb5b7/makabaka.jpg';
    DECLARE avatar_6 VARCHAR(255) DEFAULT 'public/ezchat/f01c8e35-10c3-4b5d-adcd-3b05723f01d4/d183ad39-0303-4ad7-bcf6-200a0ab2b057.png';
    DECLARE avatar_7 VARCHAR(255) DEFAULT 'public/ezchat/cb389c26-f1e7-4bd7-bfc1-40434fd12607/QQ20250917-142543.png';
    DECLARE avatar_8 VARCHAR(255) DEFAULT 'public/ezchat/c709e063-1ca1-4424-afbf-cb0f6b73a27c/80acbfed-ad9f-4327-8adc-66fff69dbe4f_e7fe6489-1e91-46ba-94b2-40136d6f8de9.png';
    DECLARE avatar_9 VARCHAR(255) DEFAULT 'public/ezchat/0ca84aad-5506-4058-9836-fb5fa64d66a9/51F-jNF1frL._AC_UF894_1000_QL80.jpg';
    DECLARE avatar_10 VARCHAR(255) DEFAULT 'public/ezchat/8743e13e-7305-474e-b494-6c9ecb74fad8/file.jpg';
    DECLARE avatar_11 VARCHAR(255) DEFAULT 'public/ezchat/addfa91a-4e38-477f-a7f1-0d9a158cb5b7/makabaka.jpg';
    DECLARE avatar_12 VARCHAR(255) DEFAULT 'public/ezchat/490f1491-17ee-45c3-88fe-7ce7cef7806e/USA.jpg';
    DECLARE avatar_13 VARCHAR(255) DEFAULT 'public/ezchat/9ccf18bb-a3be-4f9e-9421-0162b512daa6/gandam.jpg';
    DECLARE avatar_14 VARCHAR(255) DEFAULT 'public/ezchat/1d098f35-4e1f-4116-8b6e-342dd921148c/0207phn003.jpg';
    DECLARE avatar_15 VARCHAR(255) DEFAULT 'public/ezchat/1d098f35-4e1f-4116-8b6e-342dd921148c/0207phn003.jpg';
    DECLARE avatar_16 VARCHAR(255) DEFAULT 'public/ezchat/1d098f35-4e1f-4116-8b6e-342dd921148c/0207phn003.jpg';
    DECLARE avatar_17 VARCHAR(255) DEFAULT 'public/ezchat/61d1bf35-2d8b-48f9-926b-a64fbf7cef73/Chouju_sumo2.jpg';
    DECLARE avatar_18 VARCHAR(255) DEFAULT 'public/ezchat/5c567263-c29f-412d-9513-31f852fb1a8c/360_F_460031310_ObbCLA1tKrqjsHa7je6G6BSa7iAYBANP.jpg';
    DECLARE avatar_19 VARCHAR(255) DEFAULT 'public/ezchat/1d82344e-6d8f-46ad-945f-fa7dccc30949/73024655.jpg';
    DECLARE avatar_20 VARCHAR(255) DEFAULT 'public/ezchat/8f575445-952c-4e37-81fe-82acd96feea5/wenquan.jpg';


    -- List B: Message Images (14个)
    DECLARE img_1 VARCHAR(255) DEFAULT 'private/ezchat/69445c53-57c8-4ea4-9949-3c31aecbec64/android-chrome-512x512.png';
    DECLARE img_2 VARCHAR(255) DEFAULT 'public/ezchat/bbf13e53-ba8c-45c8-8ea9-8fc8e963a00c/03.jpg';
    DECLARE img_3 VARCHAR(255) DEFAULT 'private/ezchat/12b1b563-453b-47a9-ba1e-adedaad97bc9/0207phn003.jpg';
    DECLARE img_4 VARCHAR(255) DEFAULT 'private/ezchat/1c61dfec-27e1-481b-8393-4b1c0ec20120/1.jpg';
    DECLARE img_5 VARCHAR(255) DEFAULT 'private/ezchat/256d293d-ffbc-4342-b48a-51b5a9590740/0207phn003.jpg';
    DECLARE img_6 VARCHAR(255) DEFAULT 'private/ezchat/36ebf97c-ebe9-465d-a458-365d11a839df/unnamed.jpg';
    DECLARE img_7 VARCHAR(255) DEFAULT 'private/ezchat/52b93fa5-e6d8-4edd-8fcc-78d9b5f3c60e/1.jpg';
    DECLARE img_8 VARCHAR(255) DEFAULT 'private/ezchat/53ea71e4-08c6-40a1-b4c7-f0f6dee8dc7f/file.png';
    DECLARE img_9 VARCHAR(255) DEFAULT 'private/ezchat/54bef7cc-b3ea-457b-8c92-82f0a9047ba5/unnamed.jpg';
    DECLARE img_10 VARCHAR(255) DEFAULT 'private/ezchat/58deebf7-bdcc-4086-9895-68fa25f3cf3d/1.jpg';
    DECLARE img_11 VARCHAR(255) DEFAULT 'private/ezchat/60da2c4e-65f6-403c-8558-571350e5a76a/unnamed.jpg';
    DECLARE img_12 VARCHAR(255) DEFAULT 'private/ezchat/6668942e-8189-4076-988c-1e05a4a10dce/1.jpg';
    DECLARE img_13 VARCHAR(255) DEFAULT 'private/ezchat/69445c53-57c8-4ea4-9949-3c31aecbec64/android-chrome-512x512.png';

    -- 性能优化设置
    SET FOREIGN_KEY_CHECKS = 0;
    SET UNIQUE_CHECKS = 0;
    SET AUTOCOMMIT = 0;

    -- 清空旧数据
    TRUNCATE TABLE files;
    TRUNCATE TABLE messages;
    TRUNCATE TABLE chat_members;
    TRUNCATE TABLE chats;
    TRUNCATE TABLE formal_users;
    TRUNCATE TABLE users;

    -- =============================================
    -- 1. 生成 120 个用户 (多语言混合)
    -- =============================================
    SET i = 1;
    WHILE i <= 120 DO
            SET avatar_idx = ((i - 1) % 20) + 1;

            -- 修复：这里使用 uid 字段名
            INSERT INTO users (id, uid, nickname, avatar_object, bio, last_seen_at, create_time)
            VALUES (
                       i,
                       CONCAT('1000', LPAD(i, 6, '0')),
                       CASE
                           -- 日语
                           WHEN i <= 30 THEN
                               CASE (i % 5)
                                   WHEN 0 THEN CONCAT('田中 ', i)
                                   WHEN 1 THEN CONCAT('佐藤 ', i)
                                   WHEN 2 THEN CONCAT('鈴木 ', i)
                                   WHEN 3 THEN CONCAT('高橋 ', i)
                                   ELSE CONCAT('伊藤 ', i)
                                   END
                           -- 英语
                           WHEN i <= 60 THEN
                               CASE (i % 5)
                                   WHEN 0 THEN CONCAT('Alex ', i)
                                   WHEN 1 THEN CONCAT('Sarah ', i)
                                   WHEN 2 THEN CONCAT('John ', i)
                                   WHEN 3 THEN CONCAT('Emma ', i)
                                   ELSE CONCAT('Mike ', i)
                                   END
                           -- 中文
                           WHEN i <= 90 THEN
                               CASE (i % 5)
                                   WHEN 0 THEN CONCAT('李明 ', i)
                                   WHEN 1 THEN CONCAT('王伟 ', i)
                                   WHEN 2 THEN CONCAT('张伟 ', i)
                                   WHEN 3 THEN CONCAT('刘洋 ', i)
                                   ELSE CONCAT('陈静 ', i)
                                   END
                           -- 韩语
                           ELSE
                               CASE (i % 5)
                                   WHEN 0 THEN CONCAT('김철수 ', i)
                                   WHEN 1 THEN CONCAT('이영희 ', i)
                                   WHEN 2 THEN CONCAT('박지민 ', i)
                                   WHEN 3 THEN CONCAT('최민호 ', i)
                                   ELSE CONCAT('정수빈 ', i)
                                   END
                           END,
                       CASE avatar_idx
                           WHEN 1 THEN avatar_1
                           WHEN 2 THEN avatar_2
                           WHEN 3 THEN avatar_3
                           WHEN 4 THEN avatar_4
                           WHEN 5 THEN avatar_5
                           WHEN 6 THEN avatar_6
                           WHEN 7 THEN avatar_7
                           WHEN 8 THEN avatar_8
                           WHEN 9 THEN avatar_9
                           WHEN 10 THEN avatar_10
                           WHEN 11 THEN avatar_11
                           WHEN 12 THEN avatar_12
                           WHEN 13 THEN avatar_13
                           WHEN 14 THEN avatar_14
                           WHEN 15 THEN avatar_15
                           WHEN 16 THEN avatar_16
                           WHEN 17 THEN avatar_17
                           WHEN 18 THEN avatar_18
                           WHEN 19 THEN avatar_19
                           ELSE avatar_20
                           END,
                       CASE
                           WHEN i <= 30 THEN CONCAT('よろしくお願いします。JP', i)
                           WHEN i <= 60 THEN CONCAT('Nice to meet you. EN', i)
                           WHEN i <= 90 THEN CONCAT('请多关照。CN', i)
                           ELSE CONCAT('반갑습니다. KR', i)
                           END,
                       DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 1440) MINUTE),
                       DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30) DAY)
                   );

            SET i = i + 1;
        END WHILE;
    COMMIT;

    -- =============================================
    -- 2. 生成 120 个正式用户
    -- =============================================
    SET i = 1;
    WHILE i <= 120 DO
            INSERT INTO formal_users (user_id, username, password_hash, create_time, last_login_time)
            VALUES (
                       i,
                       CASE
                           WHEN i <= 30 THEN
                               CASE (i % 5)
                                   WHEN 0 THEN CONCAT('tanaka', i)
                                   WHEN 1 THEN CONCAT('sato', i)
                                   WHEN 2 THEN CONCAT('suzuki', i)
                                   WHEN 3 THEN CONCAT('takahashi', i)
                                   ELSE CONCAT('ito', i)
                                   END
                           WHEN i <= 60 THEN
                               CASE (i % 5)
                                   WHEN 0 THEN CONCAT('alex', i)
                                   WHEN 1 THEN CONCAT('sarah', i)
                                   WHEN 2 THEN CONCAT('john', i)
                                   WHEN 3 THEN CONCAT('emma', i)
                                   ELSE CONCAT('mike', i)
                                   END
                           WHEN i <= 90 THEN
                               CASE (i % 5)
                                   WHEN 0 THEN CONCAT('liming', i)
                                   WHEN 1 THEN CONCAT('wangwei', i)
                                   WHEN 2 THEN CONCAT('zhangwei', i)
                                   WHEN 3 THEN CONCAT('liuyang', i)
                                   ELSE CONCAT('chenjing', i)
                                   END
                           ELSE
                               CASE (i % 5)
                                   WHEN 0 THEN CONCAT('kim', i)
                                   WHEN 1 THEN CONCAT('lee', i)
                                   WHEN 2 THEN CONCAT('park', i)
                                   WHEN 3 THEN CONCAT('choi', i)
                                   ELSE CONCAT('jung', i)
                                   END
                           END,
                       password_hash,
                       DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30) DAY),
                       DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 1440) MINUTE)
                   );

            SET i = i + 1;
        END WHILE;
    COMMIT;

    -- =============================================
    -- 3. 生成 25 个聊天室
    -- =============================================
    SET i = 1;
    WHILE i <= 25 DO
            SET avatar_idx = ((i - 1) % 4) + 1;
            SET chat_owner = FLOOR(1 + RAND() * 120);

            INSERT INTO chats (id, chat_code, chat_name, owner_id, chat_password_hash, join_enabled, avatar_name, create_time)
            VALUES (
                       i,
                       CAST(20000000 + i AS CHAR),
                       CASE
                           WHEN i = 1 THEN 'Global Lounge'
                           WHEN i = 2 THEN '雜談 / Chat'
                           WHEN i = 3 THEN 'プロジェクトA'
                           WHEN i = 4 THEN 'Project B'
                           WHEN i = 5 THEN 'Lunch Group'
                           WHEN i = 6 THEN 'Game / ゲーム'
                           WHEN i = 7 THEN 'Study Group'
                           WHEN i = 8 THEN 'Tech Talk'
                           WHEN i = 9 THEN 'Design Team'
                           WHEN i = 10 THEN 'Marketing'
                           WHEN i = 11 THEN 'Sales'
                           WHEN i = 12 THEN 'Dev Team'
                           WHEN i = 13 THEN 'QA Team'
                           WHEN i = 14 THEN 'HR'
                           WHEN i = 15 THEN 'General Affairs'
                           WHEN i = 16 THEN 'Meeting'
                           WHEN i = 17 THEN 'Managers'
                           WHEN i = 18 THEN 'Newcomers'
                           WHEN i = 19 THEN 'Events'
                           WHEN i = 20 THEN 'Books'
                           WHEN i = 21 THEN 'Travel'
                           WHEN i = 22 THEN 'Football'
                           WHEN i = 23 THEN 'Music'
                           WHEN i = 24 THEN 'Cooking'
                           ELSE 'Others'
                           END,
                       chat_owner,
                       CASE WHEN i > 15 THEN password_hash ELSE NULL END,
                       1,
                       CASE avatar_idx
                           WHEN 1 THEN avatar_1
                           WHEN 2 THEN avatar_2
                           WHEN 3 THEN avatar_3
                           ELSE avatar_4
                           END,
                       DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30) DAY)
                   );

            SET i = i + 1;
        END WHILE;
    COMMIT;

    -- =============================================
    -- 4. 生成聊天成员关系
    -- =============================================

    -- 4.1 核心：每个房间的群主自动加入
    INSERT INTO chat_members (chat_id, user_id, last_seen_at, create_time)
    SELECT id, owner_id, NOW(), create_time FROM chats;

    -- 4.2 补充：添加一些具有随机时间的成员记录
    SET i = 1;
    WHILE i <= 25 DO
            SET j = 1;
            WHILE j <= FLOOR(5 + RAND() * 6) DO
                    SET rand_user_id = FLOOR(1 + RAND() * 120);
                    INSERT IGNORE INTO chat_members (chat_id, user_id, last_seen_at, create_time)
                    VALUES (
                               i,
                               rand_user_id,
                               DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 1440) MINUTE),
                               DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30) DAY)
                           );
                    SET j = j + 1;
                END WHILE;
            SET i = i + 1;
        END WHILE;

    -- 4.3 强制全员入群
    INSERT INTO chat_members (chat_id, user_id, create_time, update_time, last_seen_at)
    SELECT c.id, u.id,
           DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30) DAY), -- create_time
           DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30) DAY), -- update_time
           DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 1440) MINUTE) -- last_seen_at
    FROM chats c
             CROSS JOIN users u
    WHERE NOT EXISTS (
        SELECT 1
        FROM chat_members cm
        WHERE cm.chat_id = c.id AND cm.user_id = u.id
    );

    COMMIT;

    -- =============================================
    -- 5. 为每个房间生成 100+ 条消息
    -- =============================================
    SET i = 1;
    WHILE i <= 25 DO
            SET msg_count = 0;
            SET j = 1;

            WHILE j <= FLOOR(100 + RAND() * 21) DO
                    SELECT user_id INTO rand_user_id
                    FROM chat_members
                    WHERE chat_id = i
                    ORDER BY RAND()
                    LIMIT 1;

                    SET msg_type = CASE WHEN RAND() < 0.7 THEN 0 ELSE 1 END;
                    SET image_idx = FLOOR(1 + RAND() * 13);

                    INSERT INTO messages (sender_id, chat_id, text, object_names, create_time)
                    VALUES (
                               rand_user_id,
                               i,
                               CASE
                                   WHEN msg_type = 0 THEN CASE (j % 20)
                                                              WHEN 1 THEN 'おはようございます！ / Good morning!'
                                                              WHEN 2 THEN '今日もよろしくお願いします。'
                                                              WHEN 3 THEN '了解しました。 / Roger.'
                                                              WHEN 4 THEN 'ありがとうございます！ / Thanks!'
                                                              WHEN 5 THEN 'いいアイデアですね。 / Good idea.'
                                                              WHEN 6 THEN '検討してみます。'
                                                              WHEN 7 THEN 'なるほど、そうですね。'
                                                              WHEN 8 THEN 'お疲れ様です。'
                                                              WHEN 9 THEN 'よろしくお願いします！'
                                                              WHEN 10 THEN '了解です。 / OK.'
                                                              WHEN 11 THEN '收到 / 了解'
                                                              WHEN 12 THEN 'Good job!'
                                                              WHEN 13 THEN '加油 / Ganbatte'
                                                              WHEN 14 THEN 'Thank you very much.'
                                                              WHEN 15 THEN '时间を確認します。'
                                                              WHEN 16 THEN '会议的议题 / Agenda'
                                                              WHEN 17 THEN '资料を確認しました。'
                                                              WHEN 18 THEN 'Any questions?'
                                                              WHEN 19 THEN 'I will join.'
                                                              ELSE 'Hello!'
                                       END
                                   ELSE NULL
                                   END,
                               CASE
                                   WHEN msg_type = 1 THEN CONCAT('["', CASE image_idx
                                                                           WHEN 1 THEN img_1
                                                                           WHEN 2 THEN img_2
                                                                           WHEN 3 THEN img_3
                                                                           WHEN 4 THEN img_4
                                                                           WHEN 5 THEN img_5
                                                                           WHEN 6 THEN img_6
                                                                           WHEN 7 THEN img_7
                                                                           WHEN 8 THEN img_8
                                                                           WHEN 9 THEN img_9
                                                                           WHEN 10 THEN img_10
                                                                           WHEN 11 THEN img_11
                                                                           WHEN 12 THEN img_12
                                                                           ELSE img_13
                                       END, '"]')
                                   ELSE NULL
                                   END,
                               DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 43200) MINUTE)
                           );

                    SET j = j + 1;
                    SET msg_count = msg_count + 1;
                END WHILE;

            SET i = i + 1;
        END WHILE;
    COMMIT;

    SET FOREIGN_KEY_CHECKS = 1;
    SET UNIQUE_CHECKS = 1;
    SET AUTOCOMMIT = 1;

    SELECT 'Multi-language test data generation completed!' AS result;
    SELECT CONCAT('Users: ', COUNT(*)) AS summary FROM users;
    SELECT CONCAT('Chats: ', COUNT(*)) AS summary FROM chats;
    SELECT CONCAT('Messages: ', COUNT(*)) AS summary FROM messages;
    SELECT CONCAT('Chat Members: ', COUNT(*)) AS summary FROM chat_members;
END$$

DELIMITER ;

-- =============================================
-- 3. 执行存储过程
-- =============================================
CALL generate_japanese_test_data();