package hal.th50743;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * EZChat 应用启动类
 * <p>
 * Spring Boot 应用程序入口，启用 MyBatis、定时任务和异步处理。
 */
@SpringBootApplication
@MapperScan("hal.th50743.mapper")
@EnableScheduling
@org.springframework.scheduling.annotation.EnableAsync
public class EzChatAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(EzChatAppApplication.class, args);
    }

}
