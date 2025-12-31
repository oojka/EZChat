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

create table chats
(
    id                 int unsigned auto_increment comment '自增主键，聊天内部ID（chat_pk）'
        primary key,
    chat_code          char(8)                               not null comment '聊天对外ID（chatId），8位字符串',
    chat_name          varchar(20) default 'New Chat'        not null comment '聊天名称 / 群名称，可由成员修改，默认“New Chat”',
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

create table users
(
    id            int unsigned auto_increment comment '自增主键，用户内部ID'
        primary key,
    u_id          char(10)                           not null comment '用户对外ID，纯数字固定10位，唯一身份标识',
    nickname      varchar(20)                        not null comment '用户昵称，建议不超过20个字符',
    avatar_object varchar(255)                       null comment '头像图片的object name',
    bio           varchar(255)                       null comment '个人简介 / 个性签名',
    last_seen_at  datetime                           not null comment '最后在线时间，用于判断离线清理',
    create_time   datetime default CURRENT_TIMESTAMP not null comment '记录创建时间（建议按UTC存储）',
    update_time   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '记录最后修改时间（建议按UTC存储）',
    constraint user_id
        unique (u_id)
)
    comment '用户表：包含所有用户（临时用户和正式用户）的基础信息' charset = utf8mb4;

create
definer = root@`%` procedure generate_mock_data()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE rand_uid INT;
    DECLARE rand_cid INT;
    DECLARE rand_type INT; -- 0=text, 1=image, 2=file (逻辑标记)
    DECLARE last_msg_id INT;
    DECLARE fake_obj_name VARCHAR(255);

    -- 1. 性能优化设置
    SET FOREIGN_KEY_CHECKS = 0;
    SET UNIQUE_CHECKS = 0;
    SET AUTOCOMMIT = 0;

    -- 2. 清空旧数据
TRUNCATE TABLE files;
TRUNCATE TABLE messages;
TRUNCATE TABLE chat_members;
TRUNCATE TABLE chats;
TRUNCATE TABLE formal_users;
TRUNCATE TABLE users;

-- =============================================
-- 3. 生成 500 个用户
-- u_id 格式: 1000000001 ~ 1000000500 (10位数字)
-- =============================================
SET i = 1;
    WHILE i <= 500 DO
            INSERT INTO users (id, u_id, nickname, avatar_object, bio, last_seen_at)
            VALUES (
                       i,
                       CONCAT('1000', LPAD(i, 6, '0')),
                       CONCAT('User_', i),
                       CONCAT('avatars/user_', i, '.png'),
                       CONCAT('Bio for user ', i),
                       DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*10000) MINUTE)
                   );
            SET i = i + 1;
END WHILE;
COMMIT;

-- =============================================
-- 4. 生成 200 个正式用户
-- =============================================
SET i = 1;
    WHILE i <= 200 DO
            INSERT INTO formal_users (user_id, username, password_hash)
            VALUES (
                       i,
                       CONCAT('user_account_', i),
                       '$2a$10$dummyhashvalueforpasswordverification'
                   );
            SET i = i + 1;
END WHILE;
COMMIT;

-- =============================================
-- 5. 生成 100 个聊天群组
-- chat_code 格式: 20000001 ~ 20000100 (8位纯数字)
-- =============================================
SET i = 1;
    WHILE i <= 100 DO
            INSERT INTO chats (id, chat_code, chat_name, owner_id, join_enabled, avatar_name)
            VALUES (
                       i,
                       CAST(20000000 + i AS CHAR),
                       CONCAT('Group #', i),
                       FLOOR(1 + RAND() * 500),
                       IF(RAND() > 0.3, 1, 0),
                       CONCAT('group_avatar_', i, '.png')
                   );
            SET i = i + 1;
END WHILE;
COMMIT;

-- =============================================
-- 6. 生成群成员关系 (约 3000 条)
-- =============================================
-- 6.1 确保群主在群内
INSERT INTO chat_members (chat_id, user_id, last_seen_at)
SELECT id, owner_id, NOW() FROM chats;

-- 6.2 随机成员填充
SET i = 1;
    WHILE i <= 3000 DO
            INSERT IGNORE INTO chat_members (chat_id, user_id, last_seen_at)
            VALUES (
                       FLOOR(1 + RAND() * 100), -- Random Chat ID
                       FLOOR(1 + RAND() * 500), -- Random User ID
                       DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*500) HOUR)
                   );
            SET i = i + 1;
END WHILE;
COMMIT;

-- =============================================
-- 7. 生成 5000 条消息
-- =============================================
SET i = 1;
    WHILE i <= 5000 DO
            -- 随机选取一条合法的群成员关系，确保发送者在群里
SELECT chat_id, user_id INTO rand_cid, rand_uid
FROM chat_members
ORDER BY RAND() LIMIT 1;

-- 随机类型: 85% 文本, 10% 图片, 5% 文件
SET rand_type = CASE
                                WHEN RAND() < 0.85 THEN 0
                                WHEN RAND() < 0.95 THEN 1
                                ELSE 2
END;

            -- 生成对象名称 (如果是多媒体消息)
            SET fake_obj_name = CASE
                                    WHEN rand_type = 1 THEN CONCAT('img_', i, '.png')
                                    WHEN rand_type = 2 THEN CONCAT('file_', i, '.pdf')
                                    ELSE NULL
END;

INSERT INTO messages (sender_id, chat_id, text, object_names, create_time)
VALUES (
           rand_uid,
           rand_cid,
           CASE rand_type
               WHEN 0 THEN CONCAT('Message #', i, ' content text.')
               WHEN 1 THEN '[Image Message]'
               WHEN 2 THEN '[File Message]'
               END,
           fake_obj_name,
           DATE_SUB(NOW(), INTERVAL FLOOR(RAND()*14400) MINUTE)
       );

SET last_msg_id = LAST_INSERT_ID();

            -- 8. 如果是多媒体消息，写入 files 表
            IF rand_type > 0 THEN
                INSERT INTO files (message_id, file_name, content_type, file_path)
                VALUES (
                           last_msg_id,
                           fake_obj_name,
                           IF(rand_type=1, 'image/png', 'application/pdf'),
                           IF(rand_type=1, '/mnt/data/images/', '/mnt/data/files/')
                       );
END IF;

            SET i = i + 1;
END WHILE;
COMMIT;

-- 9. 恢复设置
SET FOREIGN_KEY_CHECKS = 1;
    SET UNIQUE_CHECKS = 1;
    SET AUTOCOMMIT = 1;

SELECT 'Data generation completed (10x Scale, 8-digit ChatCode)' AS result;
END;

