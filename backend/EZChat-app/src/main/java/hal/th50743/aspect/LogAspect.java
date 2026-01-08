package hal.th50743.aspect;

import hal.th50743.pojo.OperationLog;
import hal.th50743.service.impl.AsyncLogService;
import hal.th50743.utils.CurrentHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 操作日志切面（增强版）
 * <p>
 * 自动拦截 Service 层的 CUD 方法，记录详细的操作日志。
 * 支持：耗时统计、成功/失败状态、请求信息、错误追踪。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final AsyncLogService asyncLogService;

    /**
     * 定义切入点：匹配 ServiceImpl 层的 CUD 方法
     * <p>
     * Service 层命名规范：
     * - CREATE: create* / add* / save*
     * - UPDATE: update* / modify*
     * - DELETE: remove* / delete*
     */
    @Pointcut("execution(* hal.th50743.service.impl.*ServiceImpl.create*(..)) || " +
            "execution(* hal.th50743.service.impl.*ServiceImpl.add*(..)) || " +
            "execution(* hal.th50743.service.impl.*ServiceImpl.save*(..)) || " +
            "execution(* hal.th50743.service.impl.*ServiceImpl.update*(..)) || " +
            "execution(* hal.th50743.service.impl.*ServiceImpl.modify*(..)) || " +
            "execution(* hal.th50743.service.impl.*ServiceImpl.remove*(..)) || " +
            "execution(* hal.th50743.service.impl.*ServiceImpl.delete*(..))")
    public void cudOperations() {
    }

    /**
     * 环绕通知：记录方法执行前后的完整信息
     *
     * @param joinPoint 切入点
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    @Around("cudOperations()")
    public Object recordLog(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        Integer status = 1; // 默认成功
        String errorMessage = null;
        Object result = null;

        try {
            // 执行目标方法
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            // 记录失败状态和错误信息
            status = 0;
            errorMessage = truncate(e.getMessage(), 1000);
            throw e;
        } finally {
            // 无论成功失败都记录日志
            try {
                long executionTime = System.currentTimeMillis() - startTime;
                saveLog(joinPoint, status, executionTime, errorMessage, result);
            } catch (Exception e) {
                log.error("Error recording operation log", e);
            }
        }
    }

    /**
     * 构建并保存日志
     */
    private void saveLog(ProceedingJoinPoint joinPoint, Integer status, long executionTime,
            String errorMessage, Object result) {
        // 1. 获取上下文信息
        Integer userId = CurrentHolder.getCurrentId();
        LocalDateTime now = LocalDateTime.now();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        String className = signature.getDeclaringType().getSimpleName();

        // 2. 从类名提取模块名（去掉 ServiceImpl 后缀）
        String module = className.replace("ServiceImpl", "");

        // 3. 从方法名提取操作类型
        String type = extractOperationType(methodName);

        // 4. 构建内容描述（包含参数值摘要）
        String content = buildContent(methodName, joinPoint.getArgs());

        // 5. 获取请求信息
        String ip = null;
        String requestPath = null;
        String requestMethod = null;
        String userAgent = null;

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            ip = getIpAddress(request);
            requestPath = request.getRequestURI();
            requestMethod = request.getMethod();
            userAgent = truncate(request.getHeader("User-Agent"), 500);
        }

        // 6. 提取目标资源ID（从方法参数中智能提取）
        String targetId = extractTargetId(joinPoint.getArgs(), result);

        // 7. 构建日志对象
        OperationLog operationLog = OperationLog.builder()
                .userId(userId)
                .module(module)
                .type(type)
                .content(content)
                .ipAddress(ip)
                .status(status)
                .executionTime(executionTime)
                .requestPath(requestPath)
                .requestMethod(requestMethod)
                .userAgent(userAgent)
                .targetId(targetId)
                .errorMessage(errorMessage)
                .createTime(now)
                .build();

        // 8. 异步保存
        asyncLogService.addLog(operationLog);
    }

    /**
     * 从方法名提取操作类型
     */
    private String extractOperationType(String methodName) {
        if (methodName.startsWith("create") || methodName.startsWith("add") || methodName.startsWith("save")) {
            return "CREATE";
        } else if (methodName.startsWith("update") || methodName.startsWith("modify")) {
            return "UPDATE";
        } else if (methodName.startsWith("delete") || methodName.startsWith("remove")) {
            return "DELETE";
        }
        return "UNKNOWN";
    }

    /**
     * 构建日志内容（包含参数值摘要）
     */
    private String buildContent(String methodName, Object[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append(methodName).append("(");

        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if (i > 0)
                    sb.append(", ");
                sb.append(summarizeArg(args[i]));
            }
        }
        sb.append(")");

        return truncate(sb.toString(), 2000);
    }

    /**
     * 参数摘要：提取关键信息而不是完整对象
     */
    private String summarizeArg(Object arg) {
        if (arg == null)
            return "null";

        String typeName = arg.getClass().getSimpleName();

        // 基本类型和字符串直接输出
        if (arg instanceof Number || arg instanceof Boolean) {
            return arg.toString();
        }
        if (arg instanceof String str) {
            return "\"" + truncate(str, 50) + "\"";
        }
        // 对象类型只输出类名
        return typeName;
    }

    /**
     * 提取目标资源ID（从参数或返回值中智能提取）
     */
    private String extractTargetId(Object[] args, Object result) {
        // 优先从第一个参数提取（通常是 userId 或 resourceId）
        if (args != null && args.length > 0) {
            Object firstArg = args[0];
            if (firstArg instanceof Integer || firstArg instanceof Long || firstArg instanceof String) {
                return String.valueOf(firstArg);
            }
        }
        return null;
    }

    /**
     * 获取IP地址
     */
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null)
            return null;
        return str.length() <= maxLength ? str : str.substring(0, maxLength - 3) + "...";
    }
}
