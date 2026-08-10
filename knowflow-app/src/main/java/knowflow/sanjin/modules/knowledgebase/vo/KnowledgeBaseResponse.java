package knowflow.sanjin.modules.knowledgebase.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public class KnowledgeBaseResponse {

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String id;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String name;

  private String description;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private boolean enabled;

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
