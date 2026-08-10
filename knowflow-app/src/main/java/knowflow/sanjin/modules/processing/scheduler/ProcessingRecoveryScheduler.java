package knowflow.sanjin.modules.processing.scheduler;

import knowflow.sanjin.common.config.RabbitProperties;
import knowflow.sanjin.modules.processing.service.ProcessingTaskService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 任务恢复扫描：启动时兜底一次 + 定时扫描，重新发布未投递/卡死的任务。 */
@Component
@EnableScheduling
public class ProcessingRecoveryScheduler {

  private final ProcessingTaskService taskService;
  private final RabbitProperties properties;

  public ProcessingRecoveryScheduler(
      ProcessingTaskService taskService, RabbitProperties properties) {
    this.taskService = taskService;
    this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onStartup() {
    taskService.recover();
  }

  @Scheduled(fixedDelayString = "${knowflow.rabbit.recovery-scan-interval:1m}")
  public void scan() {
    taskService.recover();
  }
}
