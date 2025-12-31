package hal.th50743.interceptor;

import hal.th50743.service.UserService;
import hal.th50743.utils.CurrentHolder;
import hal.th50743.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

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
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从请求头中获取 "token"
        String token = request.getHeader("token");

        // 2. 检查 Token 是否存在
        if (token == null || token.isEmpty()) {
            log.info("Token 不存在，请求被拒绝");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        // 3. 验证 Token 的合法性
        try {
            // 解析 JWT Token 获取 Claims
            Claims claims = jwtUtils.parseJwt(token);
            // 从 Claims 中获取用户唯一标识 (uId)
            String uId = claims.get("uid").toString();
            // 根据 uId 查询数据库获取用户主键 ID
            Integer userId = userService.getIdByUId(uId);
            // 将当前用户的 ID 存入 ThreadLocal，以便在后续的业务逻辑中直接获取
            CurrentHolder.setCurrentId(userId);
        } catch (Exception e) {
            // 如果解析失败（例如 Token 过期、签名不匹配等），则认为 Token 不合法
            log.info("Token 不合法，请求被拒绝" + e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 4. Token 验证通过，放行请求
        log.info("Token 合法，请求放行");
        return true;
    }

    /**
     * 在整个请求处理完毕后被调用（视图渲染之后）。
     * <p>
     * 此方法用于清理 ThreadLocal 中保存的用户信息，防止内存泄漏。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除当前线程中存储的用户 ID
        CurrentHolder.remove();
    }
}
