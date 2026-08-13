package knowflow.sanjin.modules.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Manual Note API 响应：BIGINT id 字符串化；暴露索引状态与错误摘要、单归属知识库与标签、rowVersion。 */
public class KnowledgeDocumentResponse {

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String id;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String sourceType;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String title;

  private String summary;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String content;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private int contentVersion;

  private Integer indexedVersion;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String indexStatus;

  private String indexErrorCode;

  private String indexErrorMessage;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String knowledgeBaseId;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private List<String> tags;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private int rowVersion;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private Instant createdAt;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private Instant updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getSourceType() {
    return sourceType;
  }

  public void setSourceType(String sourceType) {
    this.sourceType = sourceType;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public int getContentVersion() {
    return contentVersion;
  }

  public void setContentVersion(int contentVersion) {
    this.contentVersion = contentVersion;
  }

  public Integer getIndexedVersion() {
    return indexedVersion;
  }

  public void setIndexedVersion(Integer indexedVersion) {
    this.indexedVersion = indexedVersion;
  }

  public String getIndexStatus() {
    return indexStatus;
  }

  public void setIndexStatus(String indexStatus) {
    this.indexStatus = indexStatus;
  }

  public String getIndexErrorCode() {
    return indexErrorCode;
  }

  public void setIndexErrorCode(String indexErrorCode) {
    this.indexErrorCode = indexErrorCode;
  }

  public String getIndexErrorMessage() {
    return indexErrorMessage;
  }

  public void setIndexErrorMessage(String indexErrorMessage) {
    this.indexErrorMessage = indexErrorMessage;
  }

  public String getKnowledgeBaseId() {
    return knowledgeBaseId;
  }

  public void setKnowledgeBaseId(String knowledgeBaseId) {
    this.knowledgeBaseId = knowledgeBaseId;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public int getRowVersion() {
    return rowVersion;
  }

  public void setRowVersion(int rowVersion) {
    this.rowVersion = rowVersion;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
