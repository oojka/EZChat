package hal.th50743.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // --- Business Errors (File Upload) ---
    FILE_EMPTY(40001, "File is empty"),
    FILE_UPLOAD_ERROR(50001, "File upload failed"),
    FILE_SIZE_EXCEED(40002, "File size exceeds limit");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}