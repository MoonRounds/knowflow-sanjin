package knowflow.sanjin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot 启动类：聚合所有模块（common + modules.*）作为单一可执行应用。 */
@SpringBootApplication
public class KnowFlowApplication {

  public static void main(String[] args) {
    SpringApplication.run(KnowFlowApplication.class, args);
  }
}
