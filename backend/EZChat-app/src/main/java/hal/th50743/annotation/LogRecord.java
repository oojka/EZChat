package hal.th50743.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * <p>
 * 用于标记需要记录操作日志的方法。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogRecord {

    /**
     * 模块名称 (如 "Chat", "User")
     */
    String module();

    /**
     * 操作类型 (如 "CREATE", "DELETE")
     */
    String type();

    /**
     * 操作内容详情
     * <p>
     * 支持 SpEL 表达式 (如 "#{args[0].id}")
     */
    String content();
}
