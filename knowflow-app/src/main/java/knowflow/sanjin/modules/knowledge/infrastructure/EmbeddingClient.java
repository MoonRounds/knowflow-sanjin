package knowflow.sanjin.modules.knowledge.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import knowflow.sanjin.common.config.EmbeddingProperties;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.common.security.BaseUrlValidator;
import knowflow.sanjin.modules.knowledge.exception.RetryableIndexException;
import knowflow.sanjin.modules.knowledge.exception.TerminalIndexException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 薄 Embedding 客户端：调 OpenAI 兼容 {@code /embeddings} 端点，返回 dense vector。
 *
 * <p>与 ChatModel 完全分离（DECISIONS §12），复用 BaseUrlValidator 做 SSRF 防护。错误分类：网络/5xx/429 → {@link
 * RetryableIndexException}；401/403/维度不匹配 → {@link TerminalIndexException}。
 */
public class EmbeddingClient {

  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final EmbeddingProperties properties;
  private final BaseUrlValidator baseUrlValidator;
  private final ObjectMapper objectMapper;
  private final OkHttpClient httpClient;

  public EmbeddingClient(EmbeddingProperties properties, BaseUrlValidator baseUrlValidator) {
    this.properties = properties;
    this.baseUrlValidator = baseUrlValidator;
    this.objectMapper = new ObjectMapper();
    this.httpClient =
        new OkHttpClient.Builder()
            .connectTimeout(properties.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(properties.getReadTimeoutMillis(), TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build();
  }

  /** 批量 Embedding：输入是 title + heading path + chunk body。 */
  public List<float[]> embed(List<String> texts) {
    if (texts.isEmpty()) {
      return List.of();
    }
    baseUrlValidator.validate(properties.getBaseUrl());
    String endpoint = properties.getBaseUrl().replaceAll("/+$", "") + "/embeddings";
    String payload;
    try {
      payload =
          objectMapper.writeValueAsString(
              java.util.Map.of("model", properties.getModel(), "input", texts));
    } catch (IOException e) {
      throw new IllegalStateException("Could not serialize embedding request", e);
    }

    Request request =
        new Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer " + properties.getApiKey())
            .post(RequestBody.create(payload, JSON))
            .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        int code = response.code();
        String body = response.body() != null ? response.body().string() : "";
        if (code == 401 || code == 403) {
          throw new TerminalIndexException(
              ErrorCode.EMBEDDING_AUTH_FAILURE,
              "Embedding auth rejected (" + code + "): " + trim(body));
        }
        if (code == 429 || code >= 500) {
          throw new RetryableIndexException(
              ErrorCode.EMBEDDING_UNAVAILABLE,
              "Embedding upstream error (" + code + "): " + trim(body),
              null);
        }
        throw new TerminalIndexException(
            ErrorCode.EMBEDDING_AUTH_FAILURE, "Embedding unexpected status " + code);
      }
      String body = response.body() != null ? response.body().string() : "{}";
      return parseEmbeddings(body);
    } catch (TerminalIndexException | RetryableIndexException e) {
      throw e;
    } catch (IOException e) {
      throw new RetryableIndexException(ErrorCode.EMBEDDING_UNAVAILABLE, "Embedding IO failure", e);
    }
  }

  private List<float[]> parseEmbeddings(String body) {
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode data = root.path("data");
      List<float[]> result = new ArrayList<>();
      for (JsonNode node : data) {
        List<Double> values = new ArrayList<>();
        node.path("embedding").forEach(v -> values.add(v.asDouble()));
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
          vector[i] = values.get(i).floatValue();
        }
        if (vector.length != properties.getDimensions()) {
          throw new TerminalIndexException(
              ErrorCode.INDEX_SCHEMA_FAILURE,
              "Embedding dimension mismatch: expected "
                  + properties.getDimensions()
                  + " but got "
                  + vector.length);
        }
        result.add(vector);
      }
      return result;
    } catch (IOException e) {
      throw new RetryableIndexException(
          ErrorCode.EMBEDDING_UNAVAILABLE, "Embedding parse failure", e);
    }
  }

  private static String trim(String s) {
    return s != null && s.length() > 300 ? s.substring(0, 300) : s;
  }
}
