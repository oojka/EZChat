package hal.th50743.aspect;

import hal.th50743.annotation.LogRecord;
import hal.th50743.pojo.OperationLog;
import hal.th50743.service.impl.AsyncLogService;
import hal.th50743.utils.CurrentHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 操作日志切面
 * <p>
 * 拦截带有 @LogRecord 注解的方法，记录操作日志。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final AsyncLogService asyncLogService;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 在方法成功返回后执行
     *
     * @param joinPoint 切入点
     * @param logRecord 注解对象
     */
    @AfterReturning(pointcut = "@annotation(logRecord)", returning = "result")
    public void recordLog(JoinPoint joinPoint, LogRecord logRecord, Object result) {
        try {
            // 1. 获取上下文信息 (userId, IP) - 必须在当前线程获取，因为异步线程无法访问 ThreadLocal 和 RequestContext
            Integer userId = CurrentHolder.getCurrentId();
            String ip = getIpAddress();
            LocalDateTime now = LocalDateTime.now();

            // 2. 解析 SpEL 表达式获取动态内容
            String content = parseSpel(logRecord.content(), joinPoint, result);

            // 3. 构建日志对象
            OperationLog log = new OperationLog();
            log.setUserId(userId);
            log.setModule(logRecord.module());
            log.setType(logRecord.type());
            log.setContent(content);
            log.setIp(ip);
            log.setCreateTime(now);

            // 4. 异步保存
            asyncLogService.saveLog(log);

        } catch (Exception e) {
            hal.th50743.aspect.LogAspect.log.error("Error recording operation log", e);
        }
    }

    /**
     * 解析 SpEL 表达式
     */
    private String parseSpel(String expressionStr, JoinPoint joinPoint, Object result) {
        // 如果不包含 SpEL 表达式特征，直接返回
        if (!expressionStr.contains("#")) {
            return expressionStr;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);

        EvaluationContext context = new StandardEvaluationContext();

        // 绑定参数名和参数值到 Context
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        // 增加 args 别名支持 #{args[0]}
        context.setVariable("args", args);
        // 增加 result 别名支持 (可选，如果以后需要在日志里记录返回值)
        context.setVariable("result", result);

        try {
            Expression expression = spelParser.parseExpression(expressionStr);
            return expression.getValue(context, String.class);
        } catch (Exception e) {
            hal.th50743.aspect.LogAspect.log.warn("Failed to parse SpEL expression: {}", expressionStr, e);
            return expressionStr; // 解析失败则保存原字符串
        }
    }

    /**
     * 获取 IP 地址
     */
    private String getIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            // 对于多级代理，取第一个 IP
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            return ip;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
