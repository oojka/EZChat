package hal.th50743.exception;

import hal.th50743.pojo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 使用 @RestControllerAdvice 注解，可以捕获整个应用程序中抛出的异常，并统一处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.error("Business Exception: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<?> handleDuplicateKeyException(DuplicateKeyException e) {
        log.error("Database unique key violation:", e);
        String msg = e.getMessage();
        if (msg != null && msg.contains("Duplicate entry")) {
            try {
                String[] arr = msg.substring(msg.indexOf("Duplicate entry")).split(" ");
                if (arr.length > 2) {
                    return Result.error(ErrorCode.DATABASE_ERROR.getCode(), arr[2] + " is already exist");
                }
            } catch (Exception parsingException) {
                log.warn("Failed to parse DuplicateKeyException message", parsingException);
            }
        }
        return Result.error(ErrorCode.DATABASE_ERROR);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<?> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.error("Database data integrity violation:", e);
        String msg = e.getMessage();
        if (msg != null && msg.contains("Data truncation")) {
            return Result.error(ErrorCode.BAD_REQUEST.getCode(), "Input value too long");
        }
        return Result.error(ErrorCode.DATABASE_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("Unknown exception occurred:", e);
        // 生产环境不建议向前端暴露详细的系统异常堆栈信息
        return Result.error(ErrorCode.SYSTEM_ERROR);
    }
}
