package in.swarnabhoomi.farmlog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FarmLogApplication {
    public static void main(String[] args) { SpringApplication.run(FarmLogApplication.class, args); }
}
