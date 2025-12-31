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
    public Result handleBusinessException(BusinessException e) {
        log.error("Business Exception: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getMessage());
    }
    /**
     * 处理唯一键冲突异常 (DuplicateKeyException)
     * 当数据库执行插入或更新操作时，违反了唯一性约束（如用户名、邮箱已存在）时触发。
     * @param e 捕获到的 DuplicateKeyException 异常
     * @return 封装了错误信息的 Result 对象
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result handleDuplicateKeyException(DuplicateKeyException e) {
        log.error("数据库唯一键冲突:", e);
        String msg = e.getMessage();
        // 尝试从异常信息中解析出具体的冲突字段，这种方式依赖于数据库的错误信息格式，可能不稳定
        if (msg != null && msg.contains("Duplicate entry")) {
            try {
                String[] arr = msg.substring(msg.indexOf("Duplicate entry")).split(" ");
                if (arr.length > 2) {
                    // 返回一个更友好的提示，例如: "'some_value' is already exist"
                    return Result.error(arr[2] + " is already exist");
                }
            } catch (Exception parsingException) {
                log.warn("解析DuplicateKeyException信息失败", parsingException);
            }
        }
        // 如果解析失败，返回一个通用的错误信息
        return Result.error("Database error");
    }

    /**
     * 处理数据完整性违规异常 (DataIntegrityViolationException)
     * 例如，当某个字段的值过长，或者外键约束失败时触发。
     * @param e 捕获到的 DataIntegrityViolationException 异常
     * @return 封装了错误信息的 Result 对象
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.error("数据库数据完整性违规:", e);
        String msg = e.getMessage();
        // 尝试从异常信息中解析出具体原因
        if (msg != null && msg.contains("Data truncation")) {
            return Result.error("Input value too long");
        }
        // 其他数据完整性问题
        return Result.error("Data integrity violation");
    }

    /**
     * 处理所有其他未被捕获的异常
     * 这是最后的防线，确保任何未知异常都能被处理，并返回一个统一的、对用户友好的错误信息。
     * @param e 捕获到的 Exception 异常
     * @return 封装了通用错误信息的 Result 对象
     */
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("发生未知异常:", e);
        //TODO 为了安全，不向客户端暴露详细的异常信息
        return Result.error(e.getMessage());
    }
}
