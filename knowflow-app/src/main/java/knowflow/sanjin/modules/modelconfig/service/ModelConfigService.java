package knowflow.sanjin.modules.modelconfig.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import knowflow.sanjin.common.security.BaseUrlValidator;
import knowflow.sanjin.common.security.SecretEncryptionService;
import knowflow.sanjin.common.security.SecretRedactor;
import knowflow.sanjin.modules.modelconfig.dto.CreateModelConfigRequest;
import knowflow.sanjin.modules.modelconfig.dto.UpdateModelConfigRequest;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfig;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.entity.OwnerAiSettings;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigDisabledException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigInUseException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigNotFoundException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigRevisionChangedException;
import knowflow.sanjin.modules.modelconfig.exception.UtilityCapabilityRequiredException;
import knowflow.sanjin.modules.modelconfig.mapper.ModelConfigMapper;
import knowflow.sanjin.modules.modelconfig.mapper.ModelConfigRevisionMapper;
import knowflow.sanjin.modules.modelconfig.mapper.OwnerAiSettingsMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelConfigService {

  private final CurrentOwnerProvider currentOwnerProvider;
  private final SecretEncryptionService encryptionService;
  private final BaseUrlValidator baseUrlValidator;
  private final ModelConfigMapper modelConfigMapper;
  private final ModelConfigRevisionMapper revisionMapper;
  private final OwnerAiSettingsMapper ownerAiSettingsMapper;

  public ModelConfigService(
      CurrentOwnerProvider currentOwnerProvider,
      SecretEncryptionService encryptionService,
      BaseUrlValidator baseUrlValidator,
      ModelConfigMapper modelConfigMapper,
      ModelConfigRevisionMapper revisionMapper,
      OwnerAiSettingsMapper ownerAiSettingsMapper) {
    this.currentOwnerProvider = currentOwnerProvider;
    this.encryptionService = encryptionService;
    this.baseUrlValidator = baseUrlValidator;
    this.modelConfigMapper = modelConfigMapper;
    this.revisionMapper = revisionMapper;
    this.ownerAiSettingsMapper = ownerAiSettingsMapper;
  }

  @Transactional
  public ModelConfig create(CreateModelConfigRequest request) {
    baseUrlValidator.validate(request.getBaseUrl());
    long ownerId = currentOwnerProvider.getCurrentOwnerId();

    ModelConfig config = new ModelConfig();
    config.setOwnerId(ownerId);
    config.setDisplayName(request.getDisplayName().trim());
    config.setProviderName(request.getProviderName().trim());
    config.setEnabled(true);
    config.setDeleted(false);
    modelConfigMapper.insert(config);

    ModelConfigRevision revision = buildRevision(config.getId(), ownerId, 1, request);
    revisionMapper.insert(revision);

    config.setCurrentRevisionId(revision.getId());
    modelConfigMapper.updateById(config);
    return config;
  }

  @Transactional(readOnly = true)
  public List<ModelConfig> listForOwner() {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    return modelConfigMapper.selectList(
        new LambdaQueryWrapper<ModelConfig>()
            .eq(ModelConfig::getOwnerId, ownerId)
            .eq(ModelConfig::getDeleted, false)
            .orderByDesc(ModelConfig::getCreatedAt));
  }

  /** 批量加载一组配置的当前 Revision，避免 N+1。 */
  @Transactional(readOnly = true)
  public Map<Long, ModelConfigRevision> loadCurrentRevisions(List<ModelConfig> configs) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    Map<Long, ModelConfigRevision> result = new HashMap<>();
    if (configs.isEmpty()) {
      return result;
    }
    List<Long> revisionIds =
        configs.stream()
            .filter(c -> c.getCurrentRevisionId() != null)
            .map(ModelConfig::getCurrentRevisionId)
            .toList();
    if (revisionIds.isEmpty()) {
      return result;
    }
    List<ModelConfigRevision> revs =
        revisionMapper.selectList(
            new LambdaQueryWrapper<ModelConfigRevision>()
                .in(ModelConfigRevision::getId, revisionIds)
                .eq(ModelConfigRevision::getOwnerId, ownerId));
    for (ModelConfigRevision rev : revs) {
      result.put(rev.getModelConfigId(), rev);
    }
    return result;
  }

  @Transactional(readOnly = true)
  public ModelConfig getByIdAndOwner(Long id) {
    return getByIdAndOwnerInternal(id);
  }

  @Transactional(readOnly = true)
  public ModelConfigRevision getRevision(Long configId, Long revisionId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    ModelConfigRevision rev =
        revisionMapper.selectOne(
            new LambdaQueryWrapper<ModelConfigRevision>()
                .eq(ModelConfigRevision::getId, revisionId)
                .eq(ModelConfigRevision::getModelConfigId, configId)
                .eq(ModelConfigRevision::getOwnerId, ownerId));
    if (rev == null) {
      throw new ModelConfigNotFoundException(configId);
    }
    return rev;
  }

  /** 供 Generation 使用：按配置 ID 解析当前 Revision，校验 enabled/deleted 与 Owner 边界。 */
  @Transactional(readOnly = true)
  public ModelConfigRevision resolveRevisionForGeneration(Long configId) {
    ModelConfig config = getByIdAndOwnerInternal(configId);
    // 禁用配置不能产生新调用；删除由 getByIdAndOwnerInternal 的 deleted=false 兜住
    if (config.getEnabled() == null || !config.getEnabled()) {
      throw new ModelConfigDisabledException(configId);
    }
    return loadCurrentRevision(configId);
  }

  @Transactional(readOnly = true)
  public List<ModelConfigRevision> listRevisions(Long configId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    getByIdAndOwnerInternal(configId);
    return revisionMapper.selectList(
        new LambdaQueryWrapper<ModelConfigRevision>()
            .eq(ModelConfigRevision::getModelConfigId, configId)
            .eq(ModelConfigRevision::getOwnerId, ownerId)
            .orderByDesc(ModelConfigRevision::getRevisionNo));
  }

  /** 任一 Revision 字段发生变化时创建新 Revision 并切换 current。 */
  @Transactional
  public ModelConfig update(Long id, UpdateModelConfigRequest request) {
    ModelConfig config = getByIdAndOwnerInternal(id);

    String nextDisplayName = config.getDisplayName();
    String nextProviderName = config.getProviderName();
    if (request.getDisplayName() != null
        && !request.getDisplayName().trim().equals(config.getDisplayName())) {
      nextDisplayName = request.getDisplayName().trim();
    }
    if (request.getProviderName() != null
        && !request.getProviderName().trim().equals(config.getProviderName())) {
      nextProviderName = request.getProviderName().trim();
    }

    ModelConfigRevision current = loadCurrentRevision(config.getId());
    boolean revisionNeeded =
        !nextDisplayName.equals(current.getDisplayName())
            || !nextProviderName.equals(current.getProviderName())
            || request.getApiKey() != null
            || (request.getBaseUrl() != null
                && !request.getBaseUrl().trim().equals(current.getBaseUrl()))
            || (request.getModelName() != null
                && !request.getModelName().trim().equals(current.getModelName()))
            || (request.getTemperature() != null
                && !request.getTemperature().equals(current.getTemperature()))
            || (request.getMaxOutputTokens() != null
                && !request.getMaxOutputTokens().equals(current.getMaxOutputTokens()));

    if (revisionNeeded) {
      baseUrlValidator.validate(
          request.getBaseUrl() != null ? request.getBaseUrl() : current.getBaseUrl());
      ModelConfigRevision newRevision =
          buildRevisionFromUpdate(
              config.getId(), config.getOwnerId(), current.getRevisionNo() + 1, request, current);
      revisionMapper.insert(newRevision);

      config.setDisplayName(nextDisplayName);
      config.setProviderName(nextProviderName);
      config.setCurrentRevisionId(newRevision.getId());
      // 新 Revision 未做过能力测试，旧证据必须作废，否则 Utility 会被视为“已通过”
      config.setUtilityTestedRevisionId(null);
      config.setUtilityRouterSchemaValid(null);
      config.setUtilityCandidateSchemaValid(null);
      config.setUtilityCapabilityTestedAt(null);

      int affected =
          modelConfigMapper.update(
              null,
              new LambdaUpdateWrapper<ModelConfig>()
                  .eq(ModelConfig::getId, config.getId())
                  .eq(ModelConfig::getOwnerId, config.getOwnerId())
                  .eq(ModelConfig::getDeleted, false)
                  .set(ModelConfig::getDisplayName, nextDisplayName)
                  .set(ModelConfig::getProviderName, nextProviderName)
                  .set(ModelConfig::getCurrentRevisionId, newRevision.getId())
                  .set(ModelConfig::getUtilityTestedRevisionId, null)
                  .set(ModelConfig::getUtilityRouterSchemaValid, null)
                  .set(ModelConfig::getUtilityCandidateSchemaValid, null)
                  .set(ModelConfig::getUtilityCapabilityTestedAt, null));
      if (affected != 1) {
        throw new ModelConfigNotFoundException(id);
      }
    }
    return config;
  }

  @Transactional
  public void softDelete(Long id) {
    ModelConfig config = getByIdAndOwnerInternal(id);
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    OwnerAiSettings settings = getOrCreateSettings(ownerId);
    // 被设为默认 Chat/Utility 的配置不能删除，否则 Owner 默认设置悬空
    if ((settings.getDefaultChatModelConfigId() != null
            && settings.getDefaultChatModelConfigId().equals(id))
        || (settings.getUtilityModelConfigId() != null
            && settings.getUtilityModelConfigId().equals(id))) {
      throw new ModelConfigInUseException(
          "ModelConfig " + id + " is referenced by Owner AI settings and cannot be deleted");
    }
    config.setDeleted(true);
    modelConfigMapper.updateById(config);
  }

  @Transactional
  public void disable(Long id) {
    ModelConfig config = getByIdAndOwnerInternal(id);
    OwnerAiSettings settings = getOrCreateSettings(config.getOwnerId());
    if ((settings.getDefaultChatModelConfigId() != null
            && settings.getDefaultChatModelConfigId().equals(id))
        || (settings.getUtilityModelConfigId() != null
            && settings.getUtilityModelConfigId().equals(id))) {
      throw new ModelConfigInUseException(
          "ModelConfig " + id + " is referenced by Owner AI settings and cannot be disabled");
    }
    config.setEnabled(false);
    modelConfigMapper.updateById(config);
  }

  @Transactional
  public void enable(Long id) {
    ModelConfig config = getByIdAndOwnerInternal(id);
    config.setEnabled(true);
    modelConfigMapper.updateById(config);
  }

  // ---- Owner AI Settings ----

  @Transactional(readOnly = true)
  public OwnerAiSettings getOwnerSettings() {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    OwnerAiSettings settings = getOrCreateSettings(ownerId);
    return settings;
  }

  /**
   * 供 Router/Extraction 解析 Utility Model 当前 Revision。要求已配置、enabled 且当前 Revision 已通过
   * Router/Candidate 两类结构化能力测试；任一不满足抛异常，由调用方降级（NOT_AVAILABLE）。
   */
  @Transactional(readOnly = true)
  public ModelConfigRevision resolveUtilityRevisionForRouting() {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    OwnerAiSettings settings = getOrCreateSettings(ownerId);
    Long utilityId = settings.getUtilityModelConfigId();
    if (utilityId == null) {
      throw new ModelConfigNotFoundException(null);
    }
    ModelConfig config = getByIdAndOwnerInternal(utilityId);
    if (config.getEnabled() == null || !config.getEnabled()) {
      throw new ModelConfigDisabledException(utilityId);
    }
    if (!hasPassedUtilityCapability(config)) {
      throw new UtilityCapabilityRequiredException(utilityId);
    }
    return loadCurrentRevision(utilityId);
  }

  @Transactional
  public OwnerAiSettings updateOwnerSettings(
      Long defaultChatModelConfigId, Long utilityModelConfigId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    if (utilityModelConfigId == null) {
      throw new IllegalArgumentException("utilityModelConfigId must not be null");
    }
    ModelConfig utility = getByIdAndOwnerInternal(utilityModelConfigId);
    if (!utility.getEnabled()) {
      throw new ModelConfigDisabledException(utilityModelConfigId);
    }
    // Utility 承担 Router/Extraction 的结构化输出，未通过能力测试前不能设为默认
    if (!hasPassedUtilityCapability(utility)) {
      throw new UtilityCapabilityRequiredException(utilityModelConfigId);
    }
    if (defaultChatModelConfigId != null) {
      ModelConfig chat = getByIdAndOwnerInternal(defaultChatModelConfigId);
      if (!chat.getEnabled()) {
        throw new ModelConfigDisabledException(defaultChatModelConfigId);
      }
    }

    OwnerAiSettings settings = getOrCreateSettings(ownerId);
    settings.setDefaultChatModelConfigId(defaultChatModelConfigId);
    settings.setUtilityModelConfigId(utilityModelConfigId);
    ownerAiSettingsMapper.updateById(settings);
    return settings;
  }

  @Transactional
  public void recordUtilityCapabilityResult(
      Long configId, Long testedRevisionId, boolean routerValid, boolean candidateValid) {
    ModelConfig config = getByIdAndOwnerInternal(configId);
    // 条件更新：仅当 current Revision 仍是本次测试的 Revision 才写入证据；
    // 若测试期间被并发切换，受影响行数为 0，证据作废并抛出 409
    int affected =
        modelConfigMapper.update(
            null,
            new LambdaUpdateWrapper<ModelConfig>()
                .eq(ModelConfig::getId, configId)
                .eq(ModelConfig::getOwnerId, config.getOwnerId())
                .eq(ModelConfig::getDeleted, false)
                .eq(ModelConfig::getCurrentRevisionId, testedRevisionId)
                .set(ModelConfig::getUtilityTestedRevisionId, testedRevisionId)
                .set(ModelConfig::getUtilityRouterSchemaValid, routerValid)
                .set(ModelConfig::getUtilityCandidateSchemaValid, candidateValid)
                .set(ModelConfig::getUtilityCapabilityTestedAt, Instant.now()));
    if (affected != 1) {
      throw new ModelConfigRevisionChangedException(configId);
    }
  }

  // ---- helpers ----

  private ModelConfig getByIdAndOwnerInternal(Long id) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    ModelConfig config =
        modelConfigMapper.selectOne(
            new LambdaQueryWrapper<ModelConfig>()
                .eq(ModelConfig::getId, id)
                .eq(ModelConfig::getOwnerId, ownerId)
                .eq(ModelConfig::getDeleted, false));
    if (config == null) {
      throw new ModelConfigNotFoundException(id);
    }
    return config;
  }

  private ModelConfigRevision loadCurrentRevision(Long configId) {
    ModelConfig config = getByIdAndOwnerInternal(configId);
    ModelConfigRevision rev =
        revisionMapper.selectOne(
            new LambdaQueryWrapper<ModelConfigRevision>()
                .eq(ModelConfigRevision::getId, config.getCurrentRevisionId())
                .eq(ModelConfigRevision::getModelConfigId, configId)
                .eq(ModelConfigRevision::getOwnerId, config.getOwnerId()));
    if (rev == null) {
      throw new ModelConfigNotFoundException(configId);
    }
    return rev;
  }

  private OwnerAiSettings getOrCreateSettings(long ownerId) {
    OwnerAiSettings settings =
        ownerAiSettingsMapper.selectOne(
            new LambdaQueryWrapper<OwnerAiSettings>().eq(OwnerAiSettings::getOwnerId, ownerId));
    if (settings == null) {
      settings = new OwnerAiSettings();
      settings.setOwnerId(ownerId);
      ownerAiSettingsMapper.insert(settings);
    }
    return settings;
  }

  private static boolean hasPassedUtilityCapability(ModelConfig config) {
    return config.getCurrentRevisionId() != null
        && config.getCurrentRevisionId().equals(config.getUtilityTestedRevisionId())
        && Boolean.TRUE.equals(config.getUtilityRouterSchemaValid())
        && Boolean.TRUE.equals(config.getUtilityCandidateSchemaValid());
  }

  private ModelConfigRevision buildRevision(
      Long configId, long ownerId, int revisionNo, CreateModelConfigRequest request) {
    ModelConfigRevision rev = new ModelConfigRevision();
    rev.setModelConfigId(configId);
    rev.setOwnerId(ownerId);
    rev.setRevisionNo(revisionNo);
    rev.setProviderType("OPENAI_COMPATIBLE");
    rev.setDisplayName(request.getDisplayName().trim());
    rev.setProviderName(request.getProviderName().trim());
    rev.setBaseUrl(request.getBaseUrl().trim());
    rev.setModelName(request.getModelName().trim());
    rev.setTemperature(request.getTemperature());
    rev.setMaxOutputTokens(request.getMaxOutputTokens());
    encryptAndStoreKey(rev, request.getApiKey());
    return rev;
  }

  private ModelConfigRevision buildRevisionFromUpdate(
      Long configId,
      long ownerId,
      int revisionNo,
      UpdateModelConfigRequest request,
      ModelConfigRevision current) {
    ModelConfigRevision rev = new ModelConfigRevision();
    rev.setModelConfigId(configId);
    rev.setOwnerId(ownerId);
    rev.setRevisionNo(revisionNo);
    rev.setProviderType(current.getProviderType());
    rev.setDisplayName(
        request.getDisplayName() != null
            ? request.getDisplayName().trim()
            : current.getDisplayName());
    rev.setProviderName(
        request.getProviderName() != null
            ? request.getProviderName().trim()
            : current.getProviderName());
    rev.setBaseUrl(
        request.getBaseUrl() != null ? request.getBaseUrl().trim() : current.getBaseUrl());
    rev.setModelName(
        request.getModelName() != null ? request.getModelName().trim() : current.getModelName());
    rev.setTemperature(
        request.getTemperature() != null ? request.getTemperature() : current.getTemperature());
    rev.setMaxOutputTokens(
        request.getMaxOutputTokens() != null
            ? request.getMaxOutputTokens()
            : current.getMaxOutputTokens());
    if (request.getApiKey() != null) {
      encryptAndStoreKey(rev, request.getApiKey());
    } else {
      rev.setEncryptedApiKey(current.getEncryptedApiKey());
      rev.setApiKeyEncryptionVersion(current.getApiKeyEncryptionVersion());
      rev.setApiKeyMasked(current.getApiKeyMasked());
    }
    return rev;
  }

  private void encryptAndStoreKey(ModelConfigRevision rev, String apiKey) {
    rev.setEncryptedApiKey(encryptionService.encrypt(apiKey));
    rev.setApiKeyMasked(SecretRedactor.maskForDisplay(apiKey));
    rev.setApiKeyEncryptionVersion(encryptionService.getEncryptionVersion());
  }
}
