package knowflow.sanjin.modules.rag.dto;

/** 一条实际提供给模型的检索来源（chunk 级）。 */
public class RetrievedSource {

  private String sourceId;

  /** KnowledgeDocument id，序列化为字符串（DECISIONS §6：API 中 BIGINT ID 字符串化）。 */
  private String documentId;

  private String documentTitle;
  private String sourceType;

  /** 来源所属 KnowledgeBase id（字符串化）与显示名；老 Trace 数据可能为 null。 */
  private String knowledgeBaseId;

  private String knowledgeBaseName;
  private Integer contentVersion;
  private Integer chunkIndex;
  private String snippet;
  private float score;

  /** 是否被回答中的 {@code [Sx]} 引用（仅模型实际引用时置 true）。 */
  private boolean cited;

  public String getSourceId() {
    return sourceId;
  }

  public void setSourceId(String sourceId) {
    this.sourceId = sourceId;
  }

  public String getDocumentId() {
    return documentId;
  }

  public void setDocumentId(String documentId) {
    this.documentId = documentId;
  }

  public String getDocumentTitle() {
    return documentTitle;
  }

  public void setDocumentTitle(String documentTitle) {
    this.documentTitle = documentTitle;
  }

  public String getSourceType() {
    return sourceType;
  }

  public void setSourceType(String sourceType) {
    this.sourceType = sourceType;
  }

  public String getKnowledgeBaseId() {
    return knowledgeBaseId;
  }

  public void setKnowledgeBaseId(String knowledgeBaseId) {
    this.knowledgeBaseId = knowledgeBaseId;
  }

  public String getKnowledgeBaseName() {
    return knowledgeBaseName;
  }

  public void setKnowledgeBaseName(String knowledgeBaseName) {
    this.knowledgeBaseName = knowledgeBaseName;
  }

  public Integer getContentVersion() {
    return contentVersion;
  }

  public void setContentVersion(Integer contentVersion) {
    this.contentVersion = contentVersion;
  }

  public Integer getChunkIndex() {
    return chunkIndex;
  }

  public void setChunkIndex(Integer chunkIndex) {
    this.chunkIndex = chunkIndex;
  }

  public String getSnippet() {
    return snippet;
  }

  public void setSnippet(String snippet) {
    this.snippet = snippet;
  }

  public float getScore() {
    return score;
  }

  public void setScore(float score) {
    this.score = score;
  }

  public boolean isCited() {
    return cited;
  }

  public void setCited(boolean cited) {
    this.cited = cited;
  }
}
