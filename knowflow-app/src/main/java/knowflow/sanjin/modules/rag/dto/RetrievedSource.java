package knowflow.sanjin.modules.rag.dto;

/** 一条实际提供给模型的检索来源（chunk 级）。 */
public class RetrievedSource {

  private String sourceId;

  /** KnowledgeItem id，序列化为字符串（DECISIONS §6：API 中 BIGINT ID 字符串化）。 */
  private String itemId;

  private String itemTitle;
  private String sourceType;
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

  public String getItemId() {
    return itemId;
  }

  public void setItemId(String itemId) {
    this.itemId = itemId;
  }

  public String getItemTitle() {
    return itemTitle;
  }

  public void setItemTitle(String itemTitle) {
    this.itemTitle = itemTitle;
  }

  public String getSourceType() {
    return sourceType;
  }

  public void setSourceType(String sourceType) {
    this.sourceType = sourceType;
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
