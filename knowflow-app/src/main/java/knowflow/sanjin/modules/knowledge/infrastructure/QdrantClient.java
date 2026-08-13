package knowflow.sanjin.modules.knowledge.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import knowflow.sanjin.common.config.QdrantProperties;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.knowledge.exception.RetryableIndexException;
import knowflow.sanjin.modules.knowledge.exception.TerminalIndexException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 薄 Qdrant REST 客户端：collection 初始化与维度校验、确定性 Point ID upsert/delete、按 payload filter 删除。
 *
 * <p>Qdrant 不保存完整 Chunk 正文；metadata 仅 owner/item/chunk 关系与版本信息。网络/5xx → 可重试； 维度不匹配、payload schema
 * 错误 → 终态。
 */
public class QdrantClient {

  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final QdrantProperties properties;
  private final ObjectMapper objectMapper;
  private final OkHttpClient httpClient;

  public QdrantClient(QdrantProperties properties) {
    this.properties = properties;
    this.objectMapper = new ObjectMapper();
    this.httpClient =
        new OkHttpClient.Builder()
            .connectTimeout(properties.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(properties.getReadTimeoutMillis(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build();
  }

  /** 确保 collection 存在且维度一致；不存在则创建 dense collection。 */
  public void ensureCollection(String collectionName, int dimensions) {
    String base = baseUrl();
    Request get = new Request.Builder().url(base + "/collections/" + collectionName).get().build();
    try (Response response = httpClient.newCall(get).execute()) {
      if (response.isSuccessful()) {
        JsonNode root = objectMapper.readTree(response.body().string());
        int existing =
            root.path("result")
                .path("config")
                .path("params")
                .path("vectors")
                .path("size")
                .asInt(-1);
        if (existing != dimensions) {
          throw new TerminalIndexException(
              ErrorCode.INDEX_SCHEMA_FAILURE,
              "Qdrant collection dimension mismatch: expected " + dimensions + " got " + existing);
        }
        return;
      }
      if (response.code() != 404) {
        throw retryable("Qdrant collection check failed: " + response.code());
      }
    } catch (IOException e) {
      throw retryable("Qdrant collection check failed", e);
    }

    // 不存在则创建
    ObjectNode vectors =
        objectMapper.createObjectNode().put("size", dimensions).put("distance", "Cosine");
    ObjectNode body = objectMapper.createObjectNode().set("vectors", vectors);
    Request create =
        new Request.Builder()
            .url(base + "/collections/" + collectionName)
            .put(RequestBody.create(body.toString(), JSON))
            .build();
    try (Response response = httpClient.newCall(create).execute()) {
      if (!response.isSuccessful()) {
        throw retryable("Qdrant collection create failed: " + response.code());
      }
    } catch (IOException e) {
      throw retryable("Qdrant collection create failed", e);
    }
  }

  /** 读取当前 collection 的向量维度；collection 不存在返回 empty，查询失败抛可重试异常。 */
  public java.util.Optional<Integer> collectionDimension(String collectionName) {
    String base = baseUrl();
    Request get = new Request.Builder().url(base + "/collections/" + collectionName).get().build();
    try (Response response = httpClient.newCall(get).execute()) {
      if (response.isSuccessful()) {
        JsonNode root = objectMapper.readTree(response.body().string());
        int size =
            root.path("result")
                .path("config")
                .path("params")
                .path("vectors")
                .path("size")
                .asInt(-1);
        return size > 0 ? java.util.Optional.of(size) : java.util.Optional.empty();
      }
      if (response.code() == 404) {
        return java.util.Optional.empty();
      }
      throw retryable("Qdrant collection check failed: " + response.code());
    } catch (IOException e) {
      throw retryable("Qdrant collection check failed", e);
    }
  }

  /** Upsert 一批 Point；Point ID 由调用方确定性生成。 */
  public void upsertPoints(String collectionName, List<Point> points) {
    if (points.isEmpty()) {
      return;
    }
    ObjectNode body = objectMapper.createObjectNode();
    ArrayNode pointsNode = body.putArray("points");
    for (Point p : points) {
      ObjectNode point = pointsNode.addObject();
      point.put("id", p.pointId());
      ArrayNode vector = point.putArray("vector");
      for (float v : p.vector()) {
        vector.add(v);
      }
      point.set("payload", p.payload());
    }
    Request request =
        new Request.Builder()
            .url(baseUrl() + "/collections/" + collectionName + "/points")
            .put(RequestBody.create(body.toString(), JSON))
            .build();
    execute(request, "Qdrant upsert failed");
  }

  /** 按 payload filter 删除 Point（用于清理旧版本或移除归属）。 */
  public void deletePointsByFilter(String collectionName, Map<String, Object> filter) {
    ObjectNode body = objectMapper.createObjectNode();
    body.set("filter", objectMapper.valueToTree(filter));
    body.put("points", true);
    Request request =
        new Request.Builder()
            .url(baseUrl() + "/collections/" + collectionName + "/points/delete")
            .post(RequestBody.create(body.toString(), JSON))
            .build();
    execute(request, "Qdrant delete failed");
  }

  /** 统计匹配 filter 的 Point 数（用于删除后验证）。 */
  public long countPoints(String collectionName, Map<String, Object> filter) {
    ObjectNode body = objectMapper.createObjectNode();
    body.set("filter", objectMapper.valueToTree(filter));
    Request request =
        new Request.Builder()
            .url(baseUrl() + "/collections/" + collectionName + "/points/count")
            .post(RequestBody.create(body.toString(), JSON))
            .build();
    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw retryable("Qdrant count failed: " + response.code());
      }
      JsonNode root = objectMapper.readTree(response.body().string());
      return root.path("result").path("count").asLong();
    } catch (IOException e) {
      throw retryable("Qdrant count failed", e);
    }
  }

  /** 稠密向量检索：owner + 多知识库 OR filter，全局 Top-K，按 score 降序返回。 */
  public List<ScoredPoint> search(
      String collectionName, float[] queryVector, int topK, Map<String, Object> filter) {
    ObjectNode body = objectMapper.createObjectNode();
    ArrayNode vector = body.putArray("vector");
    for (float v : queryVector) {
      vector.add(v);
    }
    body.put("limit", Math.max(1, topK));
    body.put("with_payload", true);
    body.set("filter", objectMapper.valueToTree(filter));
    Request request =
        new Request.Builder()
            .url(baseUrl() + "/collections/" + collectionName + "/points/search")
            .post(RequestBody.create(body.toString(), JSON))
            .build();
    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw retryable("Qdrant search failed: " + response.code());
      }
      JsonNode root = objectMapper.readTree(response.body().string());
      List<ScoredPoint> result = new java.util.ArrayList<>();
      for (JsonNode node : root.path("result")) {
        String pointId = node.path("id").asText();
        float score = (float) node.path("score").asDouble();
        ObjectNode payload =
            node.path("payload").isObject()
                ? (ObjectNode) node.path("payload")
                : objectMapper.createObjectNode();
        result.add(new ScoredPoint(pointId, score, payload));
      }
      return result;
    } catch (IOException e) {
      throw retryable("Qdrant search failed", e);
    }
  }

  /** 仅更新指定 Point 的 payload，保留原向量（PATCH /points/payload）。 */
  public void setPayload(String collectionName, List<String> pointIds, ObjectNode payload) {
    if (pointIds.isEmpty()) {
      return;
    }
    ObjectNode body = objectMapper.createObjectNode();
    body.set("payload", payload);
    ArrayNode points = body.putArray("points");
    pointIds.forEach(points::add);
    Request request =
        new Request.Builder()
            .url(baseUrl() + "/collections/" + collectionName + "/points/payload")
            .patch(RequestBody.create(body.toString(), JSON))
            .build();
    execute(request, "Qdrant set-payload failed");
  }

  private void execute(Request request, String errorPrefix) {
    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw retryable(errorPrefix + ": " + response.code());
      }
    } catch (IOException e) {
      throw retryable(errorPrefix, e);
    }
  }

  private RetryableIndexException retryable(String message) {
    return new RetryableIndexException(ErrorCode.QDRANT_UNAVAILABLE, message, null);
  }

  private RetryableIndexException retryable(String message, Throwable cause) {
    return new RetryableIndexException(ErrorCode.QDRANT_UNAVAILABLE, message, cause);
  }

  private String baseUrl() {
    return properties.getBaseUrl().replaceAll("/+$", "");
  }

  /** 一个待 Upsert 的 Qdrant Point。 */
  public record Point(String pointId, float[] vector, ObjectNode payload) {}

  /** 一次检索命中的 Point：ID、相似度分数与 metadata payload（不含正文）。 */
  public record ScoredPoint(String pointId, float score, ObjectNode payload) {}
}
