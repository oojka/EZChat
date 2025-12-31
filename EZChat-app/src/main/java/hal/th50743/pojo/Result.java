package hal.th50743.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result {

    private Integer status;
    private String message;
    private Object data;

    /**
     * status = 1
     * data为null的成功返回结果
     */
    public static Result success() {
        Result result = new Result();
        result.status = 1;
        result.message = "success";
        result.data = null;
        return result;
    }

    /**
     * status = 1
     * 包装了data的成功返回结果
     */
    public static Result success(Object data) {
        Result result = new Result();
        result.status = 1;
        result.message = "success";
        result.data = data;
        return result;
    }

    /**
     * status = 0
     * 失败返回结果
     */
    public static Result error(String message) {
        Result result = new Result();
        result.status = 0;
        result.message = message;
        result.data = null;
        return result;
    }
}
