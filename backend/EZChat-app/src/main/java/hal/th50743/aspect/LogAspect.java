package hal.th50743.aspect;

import hal.th50743.pojo.OperationLog;
import hal.th50743.service.impl.AsyncLogService;
import hal.th50743.utils.CurrentHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 操作日志切面
 * <p>
 * 自动拦截 Service 层的 add/update/delete 方法，记录操作日志。
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
     * - UPDATE: update*
     * - DELETE: remove* / delete*
     */
    @Pointcut("execution(* hal.th50743.service.impl.*ServiceImpl.create*(..)) || " +
            "execution(* hal.th50743.service.impl.*ServiceImpl.add*(..)) || " +
            "execution(* hal.th50743.service.impl.*ServiceImpl.save*(..)) || " +
            "execution(* hal.th50743.service.impl.*ServiceImpl.update*(..)) || " +
            "execution(* hal.th50743.service.impl.*ServiceImpl.remove*(..)) || " +
            "execution(* hal.th50743.service.impl.*ServiceImpl.delete*(..))")
    public void cudOperations() {
    }

    /**
     * 在方法成功返回后记录日志
     *
     * @param joinPoint 切入点
     * @param result    方法返回值
     */
    @AfterReturning(pointcut = "cudOperations()", returning = "result")
    public void recordLog(JoinPoint joinPoint, Object result) {
        try {
            // 1. 获取上下文信息
            Integer userId = CurrentHolder.getCurrentId();
            String ip = getIpAddress();
            LocalDateTime now = LocalDateTime.now();

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String methodName = signature.getMethod().getName();
            String className = signature.getDeclaringType().getSimpleName();

            // 2. 从类名提取模块名（去掉 ServiceImpl 后缀）
            String module = className.replace("ServiceImpl", "");

            // 3. 从方法名提取操作类型
            String type = extractOperationType(methodName);

            // 4. 构建内容描述
            String content = buildContent(methodName, joinPoint.getArgs());

            // 5. 构建日志对象
            OperationLog operationLog = new OperationLog();
            operationLog.setUserId(userId);
            operationLog.setModule(module);
            operationLog.setType(type);
            operationLog.setContent(content);
            operationLog.setIp(ip);
            operationLog.setCreateTime(now);

            // 6. 异步保存
            asyncLogService.addLog(operationLog);

        } catch (Exception e) {
            log.error("Error recording operation log", e);
        }
    }

    /**
     * 从方法名提取操作类型
     *
     * @param methodName 方法名
     * @return 操作类型：CREATE/UPDATE/DELETE/UNKNOWN
     */
    private String extractOperationType(String methodName) {
        if (methodName.startsWith("add")) {
            return "CREATE";
        } else if (methodName.startsWith("update")) {
            return "UPDATE";
        } else if (methodName.startsWith("delete")) {
            return "DELETE";
        }
        return "UNKNOWN";
    }

    /**
     * 构建日志内容
     *
     * @param methodName 方法名
     * @param args       方法参数
     * @return 日志内容
     */
    private String buildContent(String methodName, Object[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append(methodName);
        if (args != null && args.length > 0) {
            sb.append("(");
            sb.append(Arrays.stream(args)
                    .map(arg -> arg == null ? "null" : arg.getClass().getSimpleName())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(""));
            sb.append(")");
        }
        return sb.toString();
    }

    /**
     * 获取 IP 地址
     *
     * @return IP 地址
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
