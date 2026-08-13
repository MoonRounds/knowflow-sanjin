package knowflow.sanjin.modules.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** Tag API 响应：owner 级轻量标签，BIGINT id 字符串化。 */
public class TagResponse {

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String id;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String name;

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
}
