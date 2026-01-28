package danila.www.zskbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ZskBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZskBotApplication.class, args);
    }

}
