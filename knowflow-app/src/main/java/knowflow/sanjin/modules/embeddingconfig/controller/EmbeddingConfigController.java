package knowflow.sanjin.modules.embeddingconfig.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import knowflow.sanjin.modules.embeddingconfig.dto.TestEmbeddingConfigRequest;
import knowflow.sanjin.modules.embeddingconfig.dto.UpdateEmbeddingConfigRequest;
import knowflow.sanjin.modules.embeddingconfig.service.EmbeddingConfigService;
import knowflow.sanjin.modules.embeddingconfig.vo.EmbeddingConfigResponse;
import knowflow.sanjin.modules.embeddingconfig.vo.EmbeddingVectorizeTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统级向量模型配置入口：读取当前配置、保存（保存前服务端重测）与手动向量化预检。 */
@RestController
@RequestMapping("${knowflow.api.base-path:/api/v1}")
public class EmbeddingConfigController {

  private final EmbeddingConfigService service;

  public EmbeddingConfigController(EmbeddingConfigService service) {
    this.service = service;
  }

  @GetMapping("/embedding-config")
  @Operation(operationId = "getEmbeddingConfig")
  public EmbeddingConfigResponse getCurrent() {
    return service.getCurrent();
  }

  @PutMapping("/embedding-config")
  @Operation(operationId = "updateEmbeddingConfig")
  public EmbeddingConfigResponse update(@Valid @RequestBody UpdateEmbeddingConfigRequest request) {
    return service.save(request);
  }

  @PostMapping("/embedding-config/test")
  @Operation(operationId = "testEmbeddingConfig")
  public EmbeddingVectorizeTestResult test(@Valid @RequestBody TestEmbeddingConfigRequest request) {
    return service.test(request);
  }
}
