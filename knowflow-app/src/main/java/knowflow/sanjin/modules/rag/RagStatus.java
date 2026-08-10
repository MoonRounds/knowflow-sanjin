package knowflow.sanjin.modules.rag;

/** RAG 状态：每次 Assistant 生成的可追溯 RAG 结果分类。 */
public final class RagStatus {

  private RagStatus() {}

  /** 没有可用知识库或基础能力（Utility/Embedding 未配置），跳过路由调用。 */
  public static final String NOT_AVAILABLE = "NOT_AVAILABLE";

  /** Router 判断无需 RAG。 */
  public static final String NOT_NEEDED = "NOT_NEEDED";

  /** 需要 RAG 且检索到有效上下文并注入生成。 */
  public static final String USED = "USED";

  /** 需要 RAG 但没有足够相关内容。 */
  public static final String NO_RELEVANT_CONTEXT = "NO_RELEVANT_CONTEXT";

  /** Router / Embedding / Retrieval 失败后降级为普通聊天。 */
  public static final String DEGRADED = "DEGRADED";
}
