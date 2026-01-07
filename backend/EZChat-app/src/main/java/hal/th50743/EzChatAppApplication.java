package hal.th50743;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("hal.th50743.mapper")
@EnableScheduling
@org.springframework.scheduling.annotation.EnableAsync
public class EzChatAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(EzChatAppApplication.class, args);
    }

}
