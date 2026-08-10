package knowflow.sanjin.modules.modelconfig.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import knowflow.sanjin.common.util.ApiValueParser;
import knowflow.sanjin.modules.modelconfig.assembler.ModelConfigAssembler;
import knowflow.sanjin.modules.modelconfig.dto.CreateModelConfigRequest;
import knowflow.sanjin.modules.modelconfig.dto.UpdateModelConfigRequest;
import knowflow.sanjin.modules.modelconfig.dto.UpdateOwnerAiSettingsRequest;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfig;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.entity.OwnerAiSettings;
import knowflow.sanjin.modules.modelconfig.service.ModelCapabilityService;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.modules.modelconfig.vo.ConnectionTestResult;
import knowflow.sanjin.modules.modelconfig.vo.ModelConfigResponse;
import knowflow.sanjin.modules.modelconfig.vo.ModelConfigRevisionResponse;
import knowflow.sanjin.modules.modelconfig.vo.OwnerAiSettingsResponse;
import knowflow.sanjin.modules.modelconfig.vo.UtilityCapabilityTestResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${knowflow.api.base-path:/api/v1}")
public class ModelConfigController {

  private final ModelConfigService service;
  private final ModelCapabilityService capabilityService;

  public ModelConfigController(
      ModelConfigService service, ModelCapabilityService capabilityService) {
    this.service = service;
    this.capabilityService = capabilityService;
  }

  @PostMapping("/model-configs")
  public ResponseEntity<ModelConfigResponse> create(
      @Valid @RequestBody CreateModelConfigRequest request) {
    ModelConfig config = service.create(request);
    ModelConfigRevision rev = service.getRevision(config.getId(), config.getCurrentRevisionId());
    ModelConfigResponse response = ModelConfigAssembler.toResponse(config, rev);
    return ResponseEntity.created(URI.create("/api/v1/model-configs/" + response.getId()))
        .body(response);
  }

  @GetMapping("/model-configs")
  public List<ModelConfigResponse> list() {
    List<ModelConfig> configs = service.listForOwner();
    Map<Long, ModelConfigRevision> revs = service.loadCurrentRevisions(configs);
    return configs.stream()
        .map(c -> ModelConfigAssembler.toResponse(c, revs.get(c.getId())))
        .toList();
  }

  @GetMapping("/model-configs/{id}")
  public ModelConfigResponse get(@PathVariable String id) {
    Long configId = ApiValueParser.positiveId(id, "id");
    ModelConfig config = service.getByIdAndOwner(configId);
    ModelConfigRevision rev = service.getRevision(config.getId(), config.getCurrentRevisionId());
    return ModelConfigAssembler.toResponse(config, rev);
  }

  @PutMapping("/model-configs/{id}")
  public ModelConfigResponse update(
      @PathVariable String id, @Valid @RequestBody UpdateModelConfigRequest request) {
    Long configId = ApiValueParser.positiveId(id, "id");
    ModelConfig config = service.update(configId, request);
    ModelConfigRevision rev = service.getRevision(config.getId(), config.getCurrentRevisionId());
    return ModelConfigAssembler.toResponse(config, rev);
  }

  @DeleteMapping("/model-configs/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    service.softDelete(ApiValueParser.positiveId(id, "id"));
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/model-configs/{id}/disable")
  public ResponseEntity<Void> disable(@PathVariable String id) {
    service.disable(ApiValueParser.positiveId(id, "id"));
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/model-configs/{id}/enable")
  public ResponseEntity<Void> enable(@PathVariable String id) {
    service.enable(ApiValueParser.positiveId(id, "id"));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/model-configs/{id}/revisions")
  public List<ModelConfigRevisionResponse> listRevisions(@PathVariable String id) {
    return ModelConfigAssembler.toRevisionResponseList(
        service.listRevisions(ApiValueParser.positiveId(id, "id")));
  }

  @GetMapping("/model-configs/{id}/revisions/{revisionId}")
  public ModelConfigRevisionResponse getRevision(
      @PathVariable String id, @PathVariable String revisionId) {
    return ModelConfigAssembler.toRevisionResponse(
        service.getRevision(
            ApiValueParser.positiveId(id, "id"),
            ApiValueParser.positiveId(revisionId, "revisionId")));
  }

  @PostMapping("/model-configs/{id}/test-connection")
  public ConnectionTestResult testConnection(@PathVariable String id) {
    return capabilityService.testConnection(ApiValueParser.positiveId(id, "id"));
  }

  @PostMapping("/model-configs/{id}/test-utility-capability")
  public UtilityCapabilityTestResult testUtilityCapability(@PathVariable String id) {
    return capabilityService.testUtilityCapability(ApiValueParser.positiveId(id, "id"));
  }

  @GetMapping("/owner-ai-settings")
  public OwnerAiSettingsResponse getOwnerSettings() {
    return ModelConfigAssembler.toSettingsResponse(service.getOwnerSettings());
  }

  @PutMapping("/owner-ai-settings")
  public OwnerAiSettingsResponse updateOwnerSettings(
      @Valid @RequestBody UpdateOwnerAiSettingsRequest request) {
    OwnerAiSettings settings =
        service.updateOwnerSettings(
            request.getDefaultChatModelConfigId() != null
                ? ApiValueParser.positiveId(
                    request.getDefaultChatModelConfigId(), "defaultChatModelConfigId")
                : null,
            ApiValueParser.positiveId(request.getUtilityModelConfigId(), "utilityModelConfigId"));
    return ModelConfigAssembler.toSettingsResponse(settings);
  }
}
