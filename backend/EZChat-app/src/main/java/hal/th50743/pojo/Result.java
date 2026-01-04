package hal.th50743.pojo;

import hal.th50743.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结果封装类
 * <p>
 * 用于封装所有API接口的响应数据，提供统一的成功/失败格式。
 * 遵循前后端分离架构的响应规范。
 *
 * @param <T> 响应数据的类型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> {

    /**
     * 操作状态：1 为成功，0 为失败
     * <p>
     * 业务规则：
     * - 1: 操作成功，前端可以处理 data 数据
     * - 0: 操作失败，前端需要根据 code 和 message 处理错误
     */
    private Integer status;

    /**
     * 具体的业务错误码
     * <p>
     * 业务规则：
     * - 成功时：通常为 200（ErrorCode.SUCCESS）
     * - 失败时：对应 ErrorCode 枚举中的具体错误码
     */
    private Integer code;

    /**
     * 响应消息
     * <p>
     * 业务规则：
     * - 成功时：通常为 "success"
     * - 失败时：具体的错误描述信息
     */
    private String message;

    /**
     * 响应数据
     * <p>
     * 业务规则：
     * - 成功时：包含业务数据
     * - 失败时：通常为 null
     */
    private T data;

    /**
     * status = 1, code = 200
     * data为null的成功返回结果
     */
    public static <T> Result<T> success() {
        return new Result<>(1, ErrorCode.SUCCESS.getCode(), "success", null);
    }

    /**
     * status = 1, code = 200
     * 包装了data的成功返回结果
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(1, ErrorCode.SUCCESS.getCode(), "success", data);
    }

    /**
     * status = 0
     * 失败返回结果 (基础版)
     */
    public static Result<?> error(String message) {
        return new Result<>(0, ErrorCode.SYSTEM_ERROR.getCode(), message, null);
    }

    /**
     * status = 0
     * 失败返回结果 (支持 ErrorCode 枚举)
     */
    public static Result<?> error(ErrorCode errorCode) {
        return new Result<>(0, errorCode.getCode(), errorCode.getMsg(), null);
    }

    /**
     * status = 0
     * 失败返回结果 (支持自定义信息)
     */
    public static Result<?> error(ErrorCode errorCode, String message) {
        return new Result<>(0, errorCode.getCode(), message, null);
    }

    /**
     * status = 0
     * 失败返回结果 (支持自定义 code 和 message)
     */
    public static Result<?> error(Integer code, String message) {
        return new Result<>(0, code, message, null);
    }
}
