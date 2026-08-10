package knowflow.sanjin.modules.knowledgebase.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateKnowledgeBaseRequest {

  @Size(max = 200, message = "{knowledgebase.name.max}")
  private String name;

  @Size(max = 2000, message = "{knowledgebase.description.max}")
  private String description;

  private Boolean enabled;

  @NotNull(message = "{knowledgebase.rowVersion.required}")
  private Integer rowVersion;

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

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Integer getRowVersion() {
    return rowVersion;
  }

  public void setRowVersion(Integer rowVersion) {
    this.rowVersion = rowVersion;
  }
}
