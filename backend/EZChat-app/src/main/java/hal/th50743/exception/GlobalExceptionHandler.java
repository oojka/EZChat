package hal.th50743.exception;

import hal.th50743.pojo.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
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

    /**
     * 处理参数校验异常（@Validated、@Pattern 等注解触发的校验失败）
     * <p>
     * 业务目的：
     * - 统一处理 Spring Validation 注解校验失败的情况
     * - 将校验错误信息转换为统一的 Result 响应格式
     * <p>
     * 触发场景：
     * - Controller 方法参数使用 @Pattern、@NotNull、@Size 等注解校验失败
     * - Controller 类使用 @Validated 注解，方法参数校验失败
     * <p>
     * 示例：
     * - rawHash 参数不符合正则表达式 `^[a-fA-F0-9]{64}$`
     * - 必填参数为 null
     * - 字符串长度不符合要求
     *
     * @param e 参数校验异常
     * @return 统一响应结果（错误信息）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) {
        log.error("Validation constraint violation:", e);
        // 提取第一个校验错误消息（通常只有一个参数校验失败）
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("Validation failed");
        return Result.error(ErrorCode.BAD_REQUEST.getCode(), message);
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("Unknown exception occurred:", e);
        // 生产环境不建议向前端暴露详细的系统异常堆栈信息
        return Result.error(ErrorCode.SYSTEM_ERROR);
    }
}
