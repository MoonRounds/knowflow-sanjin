package knowflow.sanjin.modules.knowledge;

/** 知识领域常量：来源类型、生命周期、索引状态与索引任务 business key 结构。 */
public final class KnowledgeConstants {

  private KnowledgeConstants() {}

  public static final String SOURCE_MANUAL_NOTE = "MANUAL_NOTE";

  public static final String STATUS_ACTIVE = "ACTIVE";
  public static final String STATUS_DELETED = "DELETED";

  public static final String INDEX_PENDING = "PENDING";
  public static final String INDEX_PROCESSING = "PROCESSING";
  public static final String INDEX_INDEXED = "INDEXED";
  public static final String INDEX_FAILED = "FAILED";

  // ---- 索引任务 business key 结构：KNOWLEDGE_ITEM:{itemId}:{contentVersion}[:SUFFIX] ----
  public static final String BUSINESS_KEY_PREFIX = "KNOWLEDGE_ITEM:";
  public static final String BUSINESS_KEY_DELIMITER = ":";
  public static final String BUSINESS_KEY_PAYLOAD_SUFFIX = ":PAYLOAD";
  public static final String BUSINESS_KEY_DELETE_SUFFIX = ":DELETE";
}
