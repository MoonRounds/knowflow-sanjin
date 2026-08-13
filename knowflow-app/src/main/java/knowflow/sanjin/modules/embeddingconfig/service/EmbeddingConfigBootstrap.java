package knowflow.sanjin.modules.embeddingconfig.service;

import knowflow.sanjin.common.config.EmbeddingProperties;
import knowflow.sanjin.common.security.SecretEncryptionService;
import knowflow.sanjin.common.security.SecretRedactor;
import knowflow.sanjin.modules.embeddingconfig.entity.EmbeddingConfig;
import knowflow.sanjin.modules.embeddingconfig.mapper.EmbeddingConfigMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动引导：无 DB 配置且 application.yml 已配置 embedding 时，seed 一行（id=1）作为初始配置。
 *
 * <p>此后以系统设置保存为准；yml 仅作首次引导，避免全新部署必须先手工配置才能索引。
 */
@Component
public class EmbeddingConfigBootstrap {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingConfigBootstrap.class);

  private final EmbeddingConfigMapper mapper;
  private final EmbeddingProperties properties;
  private final SecretEncryptionService encryptionService;
  private final CurrentOwnerProvider currentOwnerProvider;

  public EmbeddingConfigBootstrap(
      EmbeddingConfigMapper mapper,
      EmbeddingProperties properties,
      SecretEncryptionService encryptionService,
      CurrentOwnerProvider currentOwnerProvider) {
    this.mapper = mapper;
    this.properties = properties;
    this.encryptionService = encryptionService;
    this.currentOwnerProvider = currentOwnerProvider;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void seedFromProperties() {
    if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
      return;
    }
    if (mapper.selectById(1L) != null) {
      return;
    }
    EmbeddingConfig row = new EmbeddingConfig();
    row.setId(1L);
    row.setOwnerId(currentOwnerProvider.getCurrentOwnerId());
    row.setBaseUrl(properties.getBaseUrl());
    row.setModelName(properties.getModel());
    row.setDimension(properties.getDimensions());
    row.setEncryptedApiKey(encryptionService.encrypt(properties.getApiKey()));
    row.setApiKeyMasked(SecretRedactor.maskForDisplay(properties.getApiKey()));
    row.setApiKeyEncryptionVersion(encryptionService.getEncryptionVersion());
    mapper.insert(row);
    log.info(
        "Seeded system embedding config from application.yml: baseUrl={} model={} dimension={}",
        row.getBaseUrl(),
        row.getModelName(),
        row.getDimension());
  }
}
