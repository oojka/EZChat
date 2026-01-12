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
 *
 * <p>统一处理所有 Controller 抛出的异常，返回标准格式的 {@link Result} 响应。
 *
 * <h3>处理的异常类型</h3>
 * <table border="1">
 *   <tr><th>异常类型</th><th>错误码</th><th>说明</th></tr>
 *   <tr><td>{@link BusinessException}</td><td>业务错误码</td><td>业务逻辑异常</td></tr>
 *   <tr><td>{@link DuplicateKeyException}</td><td>50001</td><td>数据库唯一键冲突</td></tr>
 *   <tr><td>{@link DataIntegrityViolationException}</td><td>50001/40000</td><td>数据完整性违规</td></tr>
 *   <tr><td>{@link ConstraintViolationException}</td><td>40000</td><td>参数校验失败</td></tr>
 *   <tr><td>{@link Exception}</td><td>50000</td><td>未知系统异常</td></tr>
 * </table>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>业务异常使用 {@link BusinessException} 携带具体错误码</li>
 *   <li>系统异常统一返回 50000，不暴露详细堆栈信息</li>
 *   <li>所有异常都记录日志，便于问题排查</li>
 *   <li>返回格式统一为 {@link Result}，便于前端处理</li>
 * </ul>
 *
 * <h3>日志级别</h3>
 * <ul>
 *   <li>业务异常：ERROR（包含错误码和消息）</li>
 *   <li>数据库异常：ERROR（包含完整堆栈）</li>
 *   <li>系统异常：ERROR（包含完整堆栈）</li>
 * </ul>
 *
 * @see BusinessException 业务异常类
 * @see ErrorCode 错误码枚举
 * @see Result 统一响应格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     *
     * <p>业务异常由 Service 层主动抛出，携带具体的错误码和消息。
     *
     * @param e 业务异常
     * @return 包含业务错误码的响应结果
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.error("Business Exception: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理数据库唯一键冲突异常
     *
     * <p>当插入/更新数据违反唯一键约束时触发。
     * 尝试从异常消息中提取冲突的键值，提供更友好的错误提示。
     *
     * @param e 唯一键冲突异常
     * @return 包含数据库错误码的响应结果
     */
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

    /**
     * 处理数据完整性违规异常
     *
     * <p>包括数据截断、外键约束违反等情况。
     * 如果是数据截断（输入值过长），返回 40000 错误码。
     *
     * @param e 数据完整性违规异常
     * @return 包含错误码的响应结果
     */
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
     * 处理参数校验异常
     *
     * <p>当 {@code @Validated}、{@code @Pattern} 等注解校验失败时触发。
     * 提取第一个校验错误消息返回给前端。
     *
     * <h4>触发场景</h4>
     * <ul>
     *   <li>Controller 参数使用 {@code @Pattern}、{@code @NotNull}、{@code @Size} 等注解校验失败</li>
     *   <li>rawHash 参数不符合正则表达式 {@code ^[a-fA-F0-9]{64}$}</li>
     * </ul>
     *
     * @param e 参数校验异常
     * @return 包含校验错误消息的响应结果
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) {
        log.error("Validation constraint violation:", e);
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("Validation failed");
        return Result.error(ErrorCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 处理未知异常（兜底处理器）
     *
     * <p>捕获所有未被其他处理器处理的异常。
     * 出于安全考虑，不向前端暴露详细的系统异常堆栈信息。
     *
     * @param e 未知异常
     * @return 包含系统错误码的响应结果
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("Unknown exception occurred:", e);
        return Result.error(ErrorCode.SYSTEM_ERROR);
    }
}
