package hal.th50743.config;

import hal.th50743.interceptor.TokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类
 * <p>
 * 负责注册自定义拦截器和配置 Spring MVC 行为。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private TokenInterceptor tokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        /**
         * 注册拦截器
         * .addPathPatterns()  用于设置拦截路径，
         *         /**表示拦截所有路径
         *         /*表示拦截一级路径
         *         /user/*表示拦截/user/下的所有一级路径
         *         /user/**表示拦截/user/下的所有路径
         *
         * .excludePathPatterns() 用于设置排除拦截的路径
         */
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**");
    }

}
