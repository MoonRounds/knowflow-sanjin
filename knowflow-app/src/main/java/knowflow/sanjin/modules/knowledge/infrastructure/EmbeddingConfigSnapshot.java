package knowflow.sanjin.modules.knowledge.infrastructure;

/**
 * Embedding 调用所需的配置快照（明文 Key），供索引与检索读取当前配置、能力测试读取候选配置。
 *
 * <p>{@code dimension} 为 0 表示未知（探测场景），调用方据此跳过维度校验。
 */
public record EmbeddingConfigSnapshot(
    String baseUrl, String apiKey, String modelName, int dimension) {}
