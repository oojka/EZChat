/**
 * 错误码枚举
 * 与后端 ErrorCode.java 保持一致
 */
export enum ErrorCode {
    // =========================================
    // 成功状态码
    // =========================================
    SUCCESS = 200,

    // =========================================
    // 客户端错误 (4xxxx)
    // =========================================
    BAD_REQUEST = 40000,
    UNAUTHORIZED = 40100,
    TOKEN_EXPIRED = 40101,
    FORBIDDEN = 40300,
    NOT_FOUND = 40400,

    // =========================================
    // 业务错误：用户相关 (41xxx)
    // =========================================
    USER_NOT_FOUND = 41001,
    USER_ALREADY_EXISTS = 41002,
    INVALID_CREDENTIALS = 41003,

    // =========================================
    // 业务错误：聊天/消息相关 (42xxx)
    // =========================================
    CHAT_NOT_FOUND = 42001,
    NOT_A_MEMBER = 42002,
    PASSWORD_REQUIRED = 42003,
    PASSWORD_INCORRECT = 42004,

    // 邀请码相关
    INVITE_CODE_INVALID = 42010,
    INVITE_CODE_EXPIRED = 42011,
    INVITE_CODE_REVOKED = 42012,
    INVITE_CODE_USAGE_LIMIT_REACHED = 42013,

    // =========================================
    // 业务错误：文件相关 (43xxx)
    // =========================================
    FILE_EMPTY = 43001,
    FILE_SIZE_EXCEED = 43002,

    // =========================================
    // 服务器错误 (5xxxx)
    // =========================================
    SYSTEM_ERROR = 50000,
    DATABASE_ERROR = 50001,
    FILE_UPLOAD_ERROR = 53001,
}
