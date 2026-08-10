package knowflow.sanjin.modules.modelconfig.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import knowflow.sanjin.common.security.SecretRedactor;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfig;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.vo.ConnectionTestResult;
import knowflow.sanjin.modules.modelconfig.vo.UtilityCapabilityTestResult;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

/**
 * 测试连接与 Utility 结构化输出能力。
 *
 * <p>普通 Chat 兼容测试与 Utility 结构化能力测试分开记录。测试使用当前 Revision 构建客户端，绝不持久化 Secret；Utility 测试只持久化该 Revision
 * 的两类 Schema 通过证据。
 */
@Service
public class ModelCapabilityService {

  private final ModelConfigService modelConfigService;
  private final ModelClientFactory clientFactory;

  public ModelCapabilityService(
      ModelConfigService modelConfigService, ModelClientFactory clientFactory) {
    this.modelConfigService = modelConfigService;
    this.clientFactory = clientFactory;
  }

  /** 普通 Chat 兼容测试：只校验连接与基础文本回复，不验证结构化能力。 */
  public ConnectionTestResult testConnection(Long configId) {
    ModelConfig config = modelConfigService.getByIdAndOwner(configId);
    ModelConfigRevision revision =
        modelConfigService.getRevision(configId, config.getCurrentRevisionId());
    try {
      ChatModel model = clientFactory.create(revision);
      ChatResponse response =
          clientFactory.callWithTotalTimeout(
              () -> model.call(new Prompt(new UserMessage("Reply with exactly the word: pong"))),
              revision.getId());
      String text = clientFactory.extractText(response);
      List<ChatResponse> streamResponses =
          clientFactory.callWithTotalTimeout(
              () ->
                  model.stream(
                          new Prompt(new UserMessage("Reply with exactly the word: stream-pong")))
                      .collectList()
                      .block(),
              revision.getId());
      String streamedText =
          streamResponses == null
              ? ""
              : streamResponses.stream().map(clientFactory::extractText).reduce("", String::concat);
      boolean ok = !text.isEmpty() && !streamedText.isEmpty();
      List<String> warnings = new ArrayList<>();
      Integer outputTokens =
          response.getMetadata() != null && response.getMetadata().getUsage() != null
              ? response.getMetadata().getUsage().getCompletionTokens()
              : null;
      if (outputTokens == null) {
        warnings.add("Provider did not report output token usage");
      }
      ConnectionTestResult result = new ConnectionTestResult();
      result.setSuccess(ok);
      result.setModelName(revision.getModelName());
      result.setOutputTokenCount(outputTokens);
      result.setMessage(
          ok
              ? "Connection OK, synchronous and streaming text replies succeeded"
              : "Synchronous or streaming reply was empty");
      result.setTestedAt(Instant.now());
      result.setWarnings(List.copyOf(warnings));
      return result;
    } catch (Exception e) {
      ConnectionTestResult result = new ConnectionTestResult();
      result.setSuccess(false);
      result.setModelName(revision.getModelName());
      result.setMessage("Connection failed: " + SecretRedactor.redact(sanitize(e)));
      result.setTestedAt(Instant.now());
      result.setWarnings(List.of());
      return result;
    }
  }

  /** Utility Model 结构化输出能力测试：验证 Router 与 Candidate 两类 Schema。 */
  public UtilityCapabilityTestResult testUtilityCapability(Long configId) {
    ModelConfig config = modelConfigService.getByIdAndOwner(configId);
    ModelConfigRevision revision =
        modelConfigService.getRevision(configId, config.getCurrentRevisionId());
    boolean routerOk = false;
    boolean candidateOk = false;
    try {
      ChatModel model = clientFactory.create(revision);

      routerOk = verifyStructuredOutput(model, RouterTestSchema.class);
      candidateOk = verifyStructuredOutput(model, CandidateTestSchema.class);
      boolean success = routerOk && candidateOk;

      UtilityCapabilityTestResult result = new UtilityCapabilityTestResult();
      result.setSuccess(success);
      result.setRouterSchemaValid(routerOk);
      result.setCandidateSchemaValid(candidateOk);
      result.setMessage(
          success
              ? "Utility structured output OK (router + candidate schemas)"
              : "Utility structured output failed one or both schemas");
      result.setTestedAt(Instant.now());
      return result;
    } catch (Exception e) {
      UtilityCapabilityTestResult result = new UtilityCapabilityTestResult();
      result.setSuccess(false);
      result.setRouterSchemaValid(false);
      result.setCandidateSchemaValid(false);
      result.setMessage("Utility capability test failed: " + SecretRedactor.redact(sanitize(e)));
      result.setTestedAt(Instant.now());
      return result;
    } finally {
      modelConfigService.recordUtilityCapabilityResult(
          configId, revision.getId(), routerOk, candidateOk);
    }
  }

  private <T> boolean verifyStructuredOutput(ChatModel model, Class<T> schemaType) {
    BeanOutputConverter<T> converter = new BeanOutputConverter<>(schemaType);
    Prompt prompt =
        new Prompt(
            List.of(
                new SystemMessage(converter.getFormat()),
                new UserMessage(
                    "Return a minimal valid JSON matching the schema. Use placeholder values.")));
    ChatResponse response = clientFactory.callWithTotalTimeout(() -> model.call(prompt), -1L);
    String text = clientFactory.extractText(response);
    if (text == null || text.isBlank()) {
      return false;
    }
    T parsed = converter.convert(text);
    return parsed != null;
  }

  private String sanitize(Throwable e) {
    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    // 限制长度，避免把完整异常栈（可能含敏感信息）写入响应
    return msg.length() > 300 ? msg.substring(0, 300) : msg;
  }
}
