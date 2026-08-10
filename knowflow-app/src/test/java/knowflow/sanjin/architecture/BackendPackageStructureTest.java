package knowflow.sanjin.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import knowflow.sanjin.KnowFlowApplication;
import knowflow.sanjin.common.config.MyBatisPlusConfig;
import knowflow.sanjin.common.config.SecretKeyProperties;
import knowflow.sanjin.common.config.SecretSecurityConfig;
import knowflow.sanjin.common.controller.HealthController;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.common.exception.GlobalExceptionHandler;
import knowflow.sanjin.common.security.BaseUrlValidator;
import knowflow.sanjin.common.security.SecretEncryptionService;
import knowflow.sanjin.common.security.SecretRedactor;
import knowflow.sanjin.common.util.ApiValueParser;
import knowflow.sanjin.modules.conversation.assembler.ConversationAssembler;
import knowflow.sanjin.modules.conversation.assembler.MessageAssembler;
import knowflow.sanjin.modules.conversation.controller.ConversationController;
import knowflow.sanjin.modules.conversation.dto.CreateConversationRequest;
import knowflow.sanjin.modules.conversation.dto.UpdateConversationRequest;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.entity.Conversation;
import knowflow.sanjin.modules.conversation.exception.ConversationNotFoundException;
import knowflow.sanjin.modules.conversation.mapper.ChatMessageMapper;
import knowflow.sanjin.modules.conversation.mapper.ConversationMapper;
import knowflow.sanjin.modules.conversation.service.ConversationService;
import knowflow.sanjin.modules.conversation.vo.ConversationResponse;
import knowflow.sanjin.modules.conversation.vo.MessagePageResponse;
import knowflow.sanjin.modules.conversation.vo.MessageResponse;
import knowflow.sanjin.modules.knowledge.controller.KnowledgeItemController;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeChunk;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.entity.Tag;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemMapper;
import knowflow.sanjin.modules.knowledge.service.KnowledgeService;
import knowflow.sanjin.modules.knowledge.vo.KnowledgeItemResponse;
import knowflow.sanjin.modules.knowledgebase.assembler.KnowledgeBaseAssembler;
import knowflow.sanjin.modules.knowledgebase.controller.KnowledgeBaseController;
import knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseNotFoundException;
import knowflow.sanjin.modules.knowledgebase.mapper.KnowledgeBaseMapper;
import knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService;
import knowflow.sanjin.modules.knowledgebase.vo.KnowledgeBaseResponse;
import knowflow.sanjin.modules.modelconfig.assembler.ModelConfigAssembler;
import knowflow.sanjin.modules.modelconfig.controller.ModelConfigController;
import knowflow.sanjin.modules.modelconfig.dto.CreateModelConfigRequest;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfig;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.entity.OwnerAiSettings;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigNotFoundException;
import knowflow.sanjin.modules.modelconfig.mapper.ModelConfigMapper;
import knowflow.sanjin.modules.modelconfig.mapper.ModelConfigRevisionMapper;
import knowflow.sanjin.modules.modelconfig.mapper.OwnerAiSettingsMapper;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.modules.modelconfig.vo.ModelConfigResponse;
import knowflow.sanjin.modules.modelconfig.vo.OwnerAiSettingsResponse;
import knowflow.sanjin.modules.owner.entity.AppUser;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.mapper.ProcessingTaskMapper;
import knowflow.sanjin.modules.processing.vo.ProcessingTaskResponse;
import org.junit.jupiter.api.Test;

/** 架构约束测试：强制各模块类落在约定包路径内，防止分层漂移。 */
class BackendPackageStructureTest {

  @Test
  void keepsBootstrapClassAtRootAndSeparatesCommonConcerns() {
    assertThat(KnowFlowApplication.class.getPackageName()).isEqualTo("knowflow.sanjin");
    assertThat(HealthController.class.getPackageName())
        .isEqualTo("knowflow.sanjin.common.controller");
    assertThat(MyBatisPlusConfig.class.getPackageName()).isEqualTo("knowflow.sanjin.common.config");
    assertThat(GlobalExceptionHandler.class.getPackageName())
        .isEqualTo("knowflow.sanjin.common.exception");
    assertThat(ApiValueParser.class.getPackageName()).isEqualTo("knowflow.sanjin.common.util");
  }

  @Test
  void separatesKnowledgeBaseModuleByResponsibility() {
    assertThat(KnowledgeBaseController.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.knowledgebase.controller");
    assertThat(KnowledgeBaseService.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.knowledgebase.service");
    assertThat(KnowledgeBaseMapper.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.knowledgebase.mapper");
    assertThat(KnowledgeBase.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.knowledgebase.entity");
    assertThat(CreateKnowledgeBaseRequest.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.knowledgebase.dto");
    assertThat(KnowledgeBaseResponse.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.knowledgebase.vo");
    assertThat(KnowledgeBaseAssembler.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.knowledgebase.assembler");
    assertThat(KnowledgeBaseNotFoundException.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.knowledgebase.exception");
  }

  @Test
  void keepsOwnerTypesInsideOwnerModule() {
    assertThat(AppUser.class.getPackageName()).isEqualTo("knowflow.sanjin.modules.owner.entity");
    assertThat(CurrentOwnerProvider.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.owner.service");
  }

  @Test
  void separatesModelConfigModuleByResponsibility() {
    assertThat(ModelConfigController.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.modelconfig.controller");
    assertThat(ModelConfigService.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.modelconfig.service");
    assertThat(ModelConfigMapper.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.modelconfig.mapper");
    assertThat(ModelConfigRevisionMapper.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.modelconfig.mapper");
    assertThat(OwnerAiSettingsMapper.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.modelconfig.mapper");
    assertThat(ModelConfig.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.modelconfig.entity");
    assertThat(ModelConfigRevision.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.modelconfig.entity");
    assertThat(OwnerAiSettings.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.modelconfig.entity");
    assertThat(CreateModelConfigRequest.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.modelconfig.dto");
    assertThat(ModelConfigResponse.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.modelconfig.vo");
    assertThat(OwnerAiSettingsResponse.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.modelconfig.vo");
    assertThat(ModelConfigAssembler.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.modelconfig.assembler");
    assertThat(ModelConfigNotFoundException.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.modelconfig.exception");
  }

  @Test
  void keepsSecretAndSecurityConcernsInsideCommon() {
    assertThat(SecretKeyProperties.class.getPackageName())
        .isEqualTo("knowflow.sanjin.common.config");
    assertThat(SecretSecurityConfig.class.getPackageName())
        .isEqualTo("knowflow.sanjin.common.config");
    assertThat(SecretEncryptionService.class.getPackageName())
        .isEqualTo("knowflow.sanjin.common.security");
    assertThat(BaseUrlValidator.class.getPackageName())
        .isEqualTo("knowflow.sanjin.common.security");
    assertThat(SecretRedactor.class.getPackageName()).isEqualTo("knowflow.sanjin.common.security");
  }

  @Test
  void separatesConversationModuleByResponsibility() {
    assertThat(ConversationController.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.conversation.controller");
    assertThat(ConversationService.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.conversation.service");
    assertThat(ConversationMapper.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.conversation.mapper");
    assertThat(ChatMessageMapper.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.conversation.mapper");
    assertThat(Conversation.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.conversation.entity");
    assertThat(ChatMessage.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.conversation.entity");
    assertThat(CreateConversationRequest.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.conversation.dto");
    assertThat(UpdateConversationRequest.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.conversation.dto");
    assertThat(ConversationResponse.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.conversation.vo");
    assertThat(MessageResponse.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.conversation.vo");
    assertThat(MessagePageResponse.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.conversation.vo");
    assertThat(ConversationAssembler.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.conversation.assembler");
    assertThat(MessageAssembler.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.conversation.assembler");
    assertThat(ConversationNotFoundException.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.conversation.exception");
  }

  @Test
  void keepsErrorCodeCatalogInsideCommonError() {
    assertThat(ErrorCode.class.getPackageName()).isEqualTo("knowflow.sanjin.common.error");
  }

  @Test
  void separatesKnowledgeModuleByResponsibility() {
    assertThat(KnowledgeItemController.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.knowledge.controller");
    assertThat(KnowledgeService.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.knowledge.service");
    assertThat(KnowledgeItemMapper.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.knowledge.mapper");
    assertThat(KnowledgeItem.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.knowledge.entity");
    assertThat(KnowledgeChunk.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.knowledge.entity");
    assertThat(Tag.class.getPackageName()).isEqualTo("knowflow.sanjin.modules.knowledge.entity");
    assertThat(KnowledgeItemResponse.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.knowledge.vo");
  }

  @Test
  void separatesProcessingModuleByResponsibility() {
    assertThat(ProcessingTask.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.processing.entity");
    assertThat(ProcessingTaskMapper.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.processing.mapper");
    assertThat(ProcessingTaskResponse.class.getPackageName())
        .isEqualTo("knowflow.sanjin.modules.processing.vo");
  }
}
