package knowflow.sanjin.modules.modelconfig.service;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.core.Timeout;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import knowflow.sanjin.common.config.ModelClientProperties;
import knowflow.sanjin.common.security.BaseUrlValidator;
import knowflow.sanjin.common.security.SecretEncryptionService;
import knowflow.sanjin.common.security.SecureOkHttpClient;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.exception.ModelCallTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * 按具体 Revision 创建云端 OpenAI-Compatible ChatModel 客户端的小边界。
 *
 * <p>所有云端 Provider 共用同一 OpenAI-Compatible 基础契约；代码中不存在 DeepSeek/Qwen 等厂商 if/else。连接/读取/总体超时在此集中。
 *
 * <p>传输层在每次请求前重新解析并校验目标，且完全禁止 HTTP 重定向。
 */
@Component
public class ModelClientFactory {

  private static final Logger log = LoggerFactory.getLogger(ModelClientFactory.class);

  private final SecretEncryptionService encryptionService;
  private final BaseUrlValidator baseUrlValidator;
  private final Duration connectTimeout;
  private final Duration readTimeout;
  private final Duration totalTimeout;
  private final ExecutorService callExecutor;

  public ModelClientFactory(
      SecretEncryptionService encryptionService,
      BaseUrlValidator baseUrlValidator,
      ModelClientProperties properties) {
    this.encryptionService = encryptionService;
    this.baseUrlValidator = baseUrlValidator;
    this.connectTimeout = properties.getConnectTimeout();
    this.readTimeout = properties.getReadTimeout();
    this.totalTimeout = properties.getTotalTimeout();
    // 每个 Factory 一个小的调用线程池；total timeout 兜底由 Future.get(timeout) 保证。
    this.callExecutor = Executors.newFixedThreadPool(Math.max(1, properties.getMaxConcurrency()));
  }

  /** 按不可变 Revision 创建客户端。每次调用创建新实例，避免共享可变状态。 */
  public ChatModel create(ModelConfigRevision revision) {
    baseUrlValidator.validate(revision.getBaseUrl());
    String apiKey = encryptionService.decrypt(revision.getEncryptedApiKey());

    OpenAiChatOptions options =
        OpenAiChatOptions.builder()
            .baseUrl(revision.getBaseUrl())
            .apiKey(apiKey)
            .model(revision.getModelName())
            .temperature(revision.getTemperature() != null ? revision.getTemperature() : 0.7d)
            .maxTokens(revision.getMaxOutputTokens() != null ? revision.getMaxOutputTokens() : 2048)
            .build();

    Timeout timeout =
        Timeout.builder().connect(connectTimeout).read(readTimeout).request(totalTimeout).build();
    OpenAIClient client =
        new OpenAIClientImpl(
            ClientOptions.builder()
                .httpClient(
                    new SecureOkHttpClient(
                        baseUrlValidator,
                        revision.getBaseUrl(),
                        connectTimeout,
                        readTimeout,
                        totalTimeout))
                .baseUrl(revision.getBaseUrl())
                .apiKey(apiKey)
                .timeout(timeout)
                .maxRetries(0)
                .build());

    return OpenAiChatModel.builder()
        .openAiClient(client)
        .openAiClientAsync(client.async())
        .options(options)
        .build();
  }

  /** 在总体超时兜底下执行同步模型调用。传输层超时之外再用 {@code Future.get(totalTimeout)} 做硬性兜底，保证调用在总体时限内结束。 */
  public <T> T callWithTotalTimeout(Supplier<T> supplier, long revisionId) {
    Future<T> future = callExecutor.submit(supplier::get);
    try {
      return future.get(totalTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new ModelCallTimeoutException(
          "Model call timed out after "
              + totalTimeout.toSeconds()
              + "s (revision "
              + revisionId
              + ")",
          e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof RuntimeException re) {
        throw re;
      }
      throw new ModelCallTimeoutException("Model call failed: " + cause.getMessage(), cause);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ModelCallTimeoutException("Model call interrupted", e);
    }
  }

  /** 执行一次调用（供统计/提示并发使用）。 */
  public <T> T callWithinLimits(Supplier<T> supplier, long revisionId) {
    log.debug("Model client call for revision {}", revisionId);
    return supplier.get();
  }

  public String extractText(ChatResponse response) {
    if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
      return "";
    }
    Generation generation = response.getResult();
    if (generation == null || generation.getOutput() == null) {
      return "";
    }
    return generation.getOutput().getText();
  }
}
