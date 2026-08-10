package knowflow.sanjin.common.controller;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 轻量健康检查端点：供前端连通性探测，不含敏感状态信息。 */
@RestController
@RequestMapping("${knowflow.api.base-path:/api/v1}")
public class HealthController {

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of(
        "status", "UP",
        "service", "knowflow-app",
        "timestamp", Instant.now().toString());
  }
}
