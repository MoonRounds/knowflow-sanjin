package knowflow.sanjin.modules.knowledgebase.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 知识库 API 响应：BIGINT id 序列化为字符串；rowVersion 供 If-Match/ETag 并发控制。 */
public class KnowledgeBaseResponse {

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String id;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String name;

  private String description;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private boolean enabled;

  /** 该库活跃文档数（deleted=0）；列表接口填充，单实体接口不保证有值。 */
  private Long documentCount;

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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Long getDocumentCount() {
    return documentCount;
  }

  public void setDocumentCount(Long documentCount) {
    this.documentCount = documentCount;
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
