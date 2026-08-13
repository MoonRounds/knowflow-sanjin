package knowflow.sanjin.modules.embeddingconfig.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import knowflow.sanjin.common.config.EmbeddingProperties;
import knowflow.sanjin.common.config.QdrantProperties;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.common.security.BaseUrlValidator;
import knowflow.sanjin.common.security.SecretEncryptionService;
import knowflow.sanjin.common.security.SecretRedactor;
import knowflow.sanjin.modules.embeddingconfig.assembler.EmbeddingConfigAssembler;
import knowflow.sanjin.modules.embeddingconfig.dto.TestEmbeddingConfigRequest;
import knowflow.sanjin.modules.embeddingconfig.dto.UpdateEmbeddingConfigRequest;
import knowflow.sanjin.modules.embeddingconfig.entity.EmbeddingConfig;
import knowflow.sanjin.modules.embeddingconfig.exception.EmbeddingConfigDimensionChangeException;
import knowflow.sanjin.modules.embeddingconfig.mapper.EmbeddingConfigMapper;
import knowflow.sanjin.modules.embeddingconfig.vo.EmbeddingConfigResponse;
import knowflow.sanjin.modules.embeddingconfig.vo.EmbeddingVectorizeTestResult;
import knowflow.sanjin.modules.knowledge.exception.RetryableIndexException;
import knowflow.sanjin.modules.knowledge.exception.TerminalIndexException;
import knowflow.sanjin.modules.knowledge.infrastructure.EmbeddingClient;
import knowflow.sanjin.modules.knowledge.infrastructure.EmbeddingConfigSnapshot;
import knowflow.sanjin.modules.knowledge.infrastructure.QdrantClient;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.springframework.stereotype.Service;

/**
 * 系统级向量模型配置：读取当前配置（DB 事实源，无行回退 yml 引导）、手动预检与保存。
 *
 * <p>保存由服务端重跑一次真实向量化测试并自动探测维度；维度与 Qdrant 当前集合维度不一致时拒绝保存 （需先全量重建索引），旧配置保持生效。
 */
@Service
public class EmbeddingConfigService {

  private static final String PROBE_TEXT = "KnowFlow 向量化测试";

  private final CurrentOwnerProvider currentOwnerProvider;
  private final SecretEncryptionService encryptionService;
  private final BaseUrlValidator baseUrlValidator;
  private final EmbeddingConfigMapper mapper;
  private final EmbeddingProperties properties;
  private final EmbeddingClient embeddingClient;
  private final QdrantClient qdrantClient;
  private final QdrantProperties qdrantProperties;

  public EmbeddingConfigService(
      CurrentOwnerProvider currentOwnerProvider,
      SecretEncryptionService encryptionService,
      BaseUrlValidator baseUrlValidator,
      EmbeddingConfigMapper mapper,
      EmbeddingProperties properties,
      EmbeddingClient embeddingClient,
      QdrantClient qdrantClient,
      QdrantProperties qdrantProperties) {
    this.currentOwnerProvider = currentOwnerProvider;
    this.encryptionService = encryptionService;
    this.baseUrlValidator = baseUrlValidator;
    this.mapper = mapper;
    this.properties = properties;
    this.embeddingClient = embeddingClient;
    this.qdrantClient = qdrantClient;
    this.qdrantProperties = qdrantProperties;
  }

  /** 当前配置响应：DB 有行则 configured=true；无行回显 yml 引导默认（configured=false）。 */
  public EmbeddingConfigResponse getCurrent() {
    EmbeddingConfig row = selectSingleton();
    if (row != null) {
      return EmbeddingConfigAssembler.toResponse(row);
    }
    String apiKey = properties.getApiKey();
    return EmbeddingConfigAssembler.toBootstrapResponse(
        properties.getBaseUrl(),
        properties.getModel(),
        apiKey == null ? "" : SecretRedactor.maskForDisplay(apiKey),
        properties.getDimensions());
  }

  /** 运行时配置快照（供索引/检索调用）：DB 行解密优先，无行回退 yml。 */
  public EmbeddingConfigSnapshot getCurrentSnapshot() {
    EmbeddingConfig row = selectSingleton();
    if (row != null) {
      return new EmbeddingConfigSnapshot(
          row.getBaseUrl(),
          encryptionService.decrypt(row.getEncryptedApiKey()),
          row.getModelName(),
          row.getDimension());
    }
    return new EmbeddingConfigSnapshot(
        properties.getBaseUrl(),
        properties.getApiKey(),
        properties.getModel(),
        properties.getDimensions());
  }

  /** 手动预检：用候选配置真实调用一次向量化。非法 Base URL 抛 400；上游失败返回失败结果。 */
  public EmbeddingVectorizeTestResult test(TestEmbeddingConfigRequest request) {
    baseUrlValidator.validate(request.getBaseUrl());
    EmbeddingVectorizeTestResult result = new EmbeddingVectorizeTestResult();
    result.setModelName(request.getModelName().trim());
    EmbeddingConfigSnapshot candidate =
        new EmbeddingConfigSnapshot(
            request.getBaseUrl().trim(),
            request.getApiKey().trim(),
            request.getModelName().trim(),
            0);
    try {
      int dimension = probeDimension(candidate);
      result.setSuccess(true);
      result.setDimension(dimension);
      result.setMessage("向量化测试通过，探测到维度 " + dimension);
    } catch (RetryableIndexException | TerminalIndexException e) {
      result.setSuccess(false);
      result.setMessage("向量化测试失败: " + SecretRedactor.redact(sanitize(e)));
    }
    result.setTestedAt(Instant.now());
    return result;
  }

  /** 保存：服务端重跑真实向量化测试，通过后持久化；维度与已索引维度不一致则拒绝。 */
  public EmbeddingConfigResponse save(UpdateEmbeddingConfigRequest request) {
    baseUrlValidator.validate(request.getBaseUrl());
    long ownerId = currentOwnerProvider.getCurrentOwnerId();

    EmbeddingConfig existing = selectSingleton();
    String apiKey = resolveApiKey(request, existing);
    String baseUrl = request.getBaseUrl().trim();
    String modelName = request.getModelName().trim();

    int dimension = probeDimension(new EmbeddingConfigSnapshot(baseUrl, apiKey, modelName, 0));

    Optional<Integer> indexedDimension =
        qdrantClient.collectionDimension(qdrantProperties.getCollectionName());
    if (indexedDimension.isPresent() && indexedDimension.get() != dimension) {
      throw new EmbeddingConfigDimensionChangeException(
          "向量模型维度变更需先重建索引：当前已索引维度 " + indexedDimension.get() + "，新模型维度 " + dimension);
    }

    EmbeddingConfig target = existing != null ? existing : newEmbeddingConfig(ownerId);
    target.setBaseUrl(baseUrl);
    target.setModelName(modelName);
    target.setDimension(dimension);
    if (request.getApiKey() != null && !request.getApiKey().trim().isEmpty()) {
      target.setEncryptedApiKey(encryptionService.encrypt(apiKey));
      target.setApiKeyMasked(SecretRedactor.maskForDisplay(apiKey));
      target.setApiKeyEncryptionVersion(encryptionService.getEncryptionVersion());
    }
    if (existing == null) {
      mapper.insert(target);
    } else {
      mapper.updateById(target);
    }
    return EmbeddingConfigAssembler.toResponse(target);
  }

  /** 首次配置：id 固定为 1 的单例行。 */
  private EmbeddingConfig newEmbeddingConfig(long ownerId) {
    EmbeddingConfig row = new EmbeddingConfig();
    row.setId(1L);
    row.setOwnerId(ownerId);
    return row;
  }

  private String resolveApiKey(UpdateEmbeddingConfigRequest request, EmbeddingConfig existing) {
    if (request.getApiKey() != null && !request.getApiKey().trim().isEmpty()) {
      return request.getApiKey().trim();
    }
    if (existing != null) {
      return encryptionService.decrypt(existing.getEncryptedApiKey());
    }
    if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
      return properties.getApiKey();
    }
    throw new IllegalArgumentException("请提供向量模型 API Key");
  }

  private int probeDimension(EmbeddingConfigSnapshot candidate) {
    List<float[]> vectors = embeddingClient.embed(List.of(PROBE_TEXT), candidate);
    if (vectors.isEmpty() || vectors.get(0).length == 0) {
      throw new RetryableIndexException(ErrorCode.EMBEDDING_UNAVAILABLE, "向量化测试未返回向量");
    }
    return vectors.get(0).length;
  }

  private EmbeddingConfig selectSingleton() {
    return mapper.selectById(1L);
  }

  private static String sanitize(Throwable e) {
    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    return msg.length() > 300 ? msg.substring(0, 300) : msg;
  }
}
