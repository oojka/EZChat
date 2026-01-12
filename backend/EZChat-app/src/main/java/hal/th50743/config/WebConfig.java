package hal.th50743.config;

import hal.th50743.interceptor.TokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类
 *
 * <p>负责注册自定义拦截器和配置 Spring MVC 行为。
 *
 * <h3>拦截器配置</h3>
 * <ul>
 *   <li>{@link TokenInterceptor} - Token 验证拦截器，保护所有 API 接口</li>
 * </ul>
 *
 * <h3>路径规则</h3>
 * <ul>
 *   <li>{@code /**} - 拦截所有路径（需要 Token 认证）</li>
 *   <li>{@code /auth/**} - 排除认证相关接口（登录、注册、访客等）</li>
 * </ul>
 *
 * <h3>拦截顺序</h3>
 * <p>请求进入时：TokenInterceptor.preHandle() → Controller
 * <p>请求完成后：Controller → TokenInterceptor.afterCompletion()
 *
 * @see TokenInterceptor Token 验证拦截器
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private TokenInterceptor tokenInterceptor;

    /**
     * 注册拦截器
     *
     * <p>配置 Token 拦截器的拦截规则：
     * <ul>
     *   <li>拦截所有路径 {@code /**}</li>
     *   <li>排除认证相关路径 {@code /auth/**}</li>
     * </ul>
     *
     * <h4>路径匹配规则说明</h4>
     * <ul>
     *   <li>{@code /**} - 匹配所有路径（包括多级子路径）</li>
     *   <li>{@code /*} - 仅匹配一级路径</li>
     *   <li>{@code /user/*} - 匹配 /user/ 下的一级路径</li>
     *   <li>{@code /user/**} - 匹配 /user/ 下的所有路径</li>
     * </ul>
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**");
    }

}
