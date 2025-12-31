package hal.th50743.pojo;

import hal.th50743.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result {

    private Integer status; // 1 为成功，0 为失败
    private Integer code;   // 具体的业务错误码
    private String message;
    private Object data;

    /**
     * status = 1, code = 200
     * data为null的成功返回结果
     */
    public static Result success() {
        return new Result(1, ErrorCode.SUCCESS.getCode(), "success", null);
    }

    /**
     * status = 1, code = 200
     * 包装了data的成功返回结果
     */
    public static Result success(Object data) {
        return new Result(1, ErrorCode.SUCCESS.getCode(), "success", data);
    }

    /**
     * status = 0
     * 失败返回结果 (基础版)
     */
    public static Result error(String message) {
        return new Result(0, ErrorCode.SYSTEM_ERROR.getCode(), message, null);
    }

    /**
     * status = 0
     * 失败返回结果 (支持 ErrorCode 枚举)
     */
    public static Result error(ErrorCode errorCode) {
        return new Result(0, errorCode.getCode(), errorCode.getMsg(), null);
    }

    /**
     * status = 0
     * 失败返回结果 (支持自定义信息)
     */
    public static Result error(ErrorCode errorCode, String message) {
        return new Result(0, errorCode.getCode(), message, null);
    }

    /**
     * status = 0
     * 失败返回结果 (支持自定义 code 和 message)
     */
    public static Result error(Integer code, String message) {
        return new Result(0, code, message, null);
    }
}
