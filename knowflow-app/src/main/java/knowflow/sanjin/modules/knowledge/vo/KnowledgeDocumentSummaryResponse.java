package knowflow.sanjin.modules.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** 文档列表摘要响应：不含正文（列表不需要 content）。BIGINT id 字符串化。解析/索引两段状态与失败错误码供前端聚合展示（P4）。 */
public class KnowledgeDocumentSummaryResponse {

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String id;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String sourceType;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String title;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private int contentVersion;

  private Integer indexedVersion;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String indexStatus;

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

  /** 上传文件解析状态（PENDING/PROCESSING/SUCCEEDED/FAILED）；非上传文档为 null。 */
  private String parseStatus;

  private String parseErrorCode;
  private String parseErrorMessage;

  /** 索引失败摘要（document 列直出）；未失败为 null。 */
  private String indexErrorCode;

  private String indexErrorMessage;

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

  public String getParseStatus() {
    return parseStatus;
  }

  public void setParseStatus(String parseStatus) {
    this.parseStatus = parseStatus;
  }

  public String getParseErrorCode() {
    return parseErrorCode;
  }

  public void setParseErrorCode(String parseErrorCode) {
    this.parseErrorCode = parseErrorCode;
  }

  public String getParseErrorMessage() {
    return parseErrorMessage;
  }

  public void setParseErrorMessage(String parseErrorMessage) {
    this.parseErrorMessage = parseErrorMessage;
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
}
