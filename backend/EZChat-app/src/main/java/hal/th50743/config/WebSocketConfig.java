package hal.th50743.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 配置类
 *
 * <p>配置 WebSocket 端点自动注册，使 {@code @ServerEndpoint} 注解生效。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>{@code /websocket/{token}} - 主 WebSocket 连接端点</li>
 * </ul>
 *
 * <h3>工作原理</h3>
 * <ul>
 *   <li>注入 {@link ServerEndpointExporter} Bean</li>
 *   <li>Spring Boot 启动时自动扫描并注册所有 {@code @ServerEndpoint} 类</li>
 *   <li>支持 Jakarta WebSocket API（JSR-356）</li>
 * </ul>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>仅在使用嵌入式容器（如 Tomcat）时需要此配置</li>
 *   <li>外部容器部署时无需此 Bean（容器会自动处理）</li>
 * </ul>
 *
 * @see hal.th50743.ws.WebSocketServer WebSocket 服务端点
 */
@Configuration
public class WebSocketConfig {

    /**
     * 注册 ServerEndpointExporter Bean
     *
     * <p>该 Bean 会自动扫描并注册所有使用 {@code @ServerEndpoint} 注解的类，
     * 使其成为 WebSocket 端点。
     *
     * @return ServerEndpointExporter 实例
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
