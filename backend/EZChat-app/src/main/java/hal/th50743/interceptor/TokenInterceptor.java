package hal.th50743.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import hal.th50743.exception.ErrorCode;
import hal.th50743.pojo.Result;
import hal.th50743.service.CacheService;
import hal.th50743.service.UserService;
import hal.th50743.utils.CurrentHolder;
import hal.th50743.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Token 拦截器，用于在请求到达控制器之前验证用户身份。
 */
@Slf4j
@Component
public class TokenInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserService userService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 在请求处理之前进行调用。
     * <p>
     * 此方法用于验证请求头中的 JWT Token，以确认用户是否已登录。
     *
     * @param request  HTTP 请求对象
     * @param response HTTP 响应对象
     * @param handler  即将处理请求的处理器
     * @return 如果验证通过则返回 true，否则返回 false 并设置 HTTP 状态码为 401 (Unauthorized)。
     * @throws Exception 可能抛出的异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 1. 从请求头中获取 "token"
        String token = request.getHeader("token");

        // 2. 检查 Token 是否存在
        if (token == null || token.isEmpty()) {
            log.warn("Token missing, request from {} rejected", request.getRemoteAddr());
            writeUnauthorizedResponse(response, ErrorCode.UNAUTHORIZED, "Token is required");
            return false;
        }
        // 3. 验证 Token 的合法性
        try {
            // 解析 JWT Token 获取 Claims
            Claims claims = jwtUtils.parseJwt(token);
            String tokenType = claims.get("tokenType", String.class);
            if (!"access".equals(tokenType)) {
                log.warn("Invalid token type: {}", tokenType);
                writeUnauthorizedResponse(response, ErrorCode.UNAUTHORIZED, "Invalid token type");
                return false;
            }
            // 从 Claims 中获取用户唯一标识 (uid)
            String uid = claims.get("uid").toString();
            // 根据 uid 查询数据库获取用户主键 ID
            Integer userId = userService.getIdByUid(uid);
            if (userId == null) {
                log.warn("Token parsed but user not found: uid={}", uid);
                writeUnauthorizedResponse(response, ErrorCode.UNAUTHORIZED, "User not found");
                return false;
            }
            String cachedToken = cacheService.getAccessToken(userId);
            if (cachedToken == null || !cachedToken.equals(token)) {
                log.warn("AccessToken cache validation failed: userId={}", userId);
                writeUnauthorizedResponse(response, ErrorCode.UNAUTHORIZED, "AccessToken mismatch");
                return false;
            }
            // 将当前用户的 ID 存入 ThreadLocal，以便在后续的业务逻辑中直接获取
            CurrentHolder.setCurrentId(userId);
        } catch (ExpiredJwtException e) {
            log.warn("AccessToken expired: {}", e.getMessage());
            writeUnauthorizedResponse(response, ErrorCode.TOKEN_EXPIRED, "AccessToken expired");
            return false;
        } catch (JwtException e) {
            log.warn("Token validation failed: {}", e.getMessage());
            writeUnauthorizedResponse(response, ErrorCode.UNAUTHORIZED, "Token is invalid");
            return false;
        } catch (Exception e) {
            // 如果解析失败（例如 Token 过期、签名不匹配等），则认为 Token 不合法
            log.error("Token validation exception: {}, request rejected", e.getMessage(), e);
            writeUnauthorizedResponse(response, ErrorCode.UNAUTHORIZED, "Unauthorized");
            return false;
        }

        // 4. Token 验证通过，放行请求
        // log.info("Token validation passed, userID: {} request allowed",
        // CurrentHolder.getCurrentId());
        return true;
    }

    /**
     * 在整个请求处理完毕后被调用（视图渲染之后）。
     * <p>
     * 此方法用于清理 ThreadLocal 中保存的用户信息，防止内存泄漏。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        // 移除当前线程中存储的用户 ID
        CurrentHolder.remove();
    }

    /**
     * 写入未授权响应（JSON 格式），供前端识别错误码
     *
     * @param response  HTTP 响应对象
     * @param errorCode 业务错误码
     * @param message   错误消息
     */
    private void writeUnauthorizedResponse(HttpServletResponse response, ErrorCode errorCode, String message) {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        Result<?> result = Result.error(errorCode, message);
        try {
            response.getWriter().write(objectMapper.writeValueAsString(result));
        } catch (IOException e) {
            log.error("Failed to write unauthorized response", e);
        }
    }
}
