package knowflow.sanjin.modules.knowledgebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateKnowledgeBaseRequest {

  @NotBlank(message = "{knowledgebase.name.required}")
  @Size(max = 200, message = "{knowledgebase.name.max}")
  private String name;

  @Size(max = 2000, message = "{knowledgebase.description.max}")
  private String description;

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
}
