package hal.th50743.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * @version v1.0
 * @ClassName  WebSocketConfig
 * @Description WebSocket配置类
 */
@Configuration
public class WebSocketConfig {

    //注入ServerEndpointExporter，自动注册使用@ServerEndpoint注解的
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
