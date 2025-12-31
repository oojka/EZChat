package hal.th50743.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // --- Success ---
    SUCCESS(200, "success"),

    // --- Client Errors (4xxxx) ---
    BAD_REQUEST(40000, "Bad request"),
    UNAUTHORIZED(40100, "Unauthorized"),
    FORBIDDEN(40300, "Forbidden"),
    NOT_FOUND(40400, "Resource not found"),

    // --- Business Errors: User (41xxx) ---
    USER_NOT_FOUND(41001, "User not found"),
    USER_ALREADY_EXISTS(41002, "User already exists"),
    INVALID_CREDENTIALS(41003, "Invalid username or password"),

    // --- Business Errors: Chat/Message (42xxx) ---
    CHAT_NOT_FOUND(42001, "Chat room not found"),
    NOT_A_MEMBER(42002, "User is not a member of this chat room"),

    // --- Business Errors: File (43xxx) ---
    FILE_EMPTY(43001, "File is empty"),
    FILE_SIZE_EXCEED(43002, "File size exceeds limit"),
    FILE_UPLOAD_ERROR(53001, "File upload failed"),

    // --- Server Errors (5xxxx) ---
    SYSTEM_ERROR(50000, "System internal error"),
    DATABASE_ERROR(50001, "Database error");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}