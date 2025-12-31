package hal.th50743.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final String msg;

    // 构造方法 1: 直接传 ErrorCode (最常用)
    public BusinessException(ErrorCode errorCode) {
        //以此 message 传给父类，方便日志打印 e.getMessage()
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
        this.msg = errorCode.getMsg();
    }

    // 构造方法 2: 自定义错误信息
    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    // 构造方法 2.1: 自定义错误信息 (支持 ErrorCode)
    public BusinessException(ErrorCode errorCode, String msg) {
        super(msg);
        this.code = errorCode.getCode();
        this.msg = msg;
    }

    // 构造方法 3: 只有消息
    public BusinessException(String msg) {
        super(msg);
        this.code = ErrorCode.SYSTEM_ERROR.getCode(); // 使用系统错误码作为默认
        this.msg = msg;
    }
}