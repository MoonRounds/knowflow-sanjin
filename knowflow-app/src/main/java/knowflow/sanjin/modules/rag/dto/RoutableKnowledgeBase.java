package knowflow.sanjin.modules.rag.dto;

/** 提供给 Router 的一个可路由 KnowledgeBase 条目（仅目录信息，不含正文）。 */
public class RoutableKnowledgeBase {

  private Long id;
  private String name;
  private String description;

  public RoutableKnowledgeBase() {}

  public RoutableKnowledgeBase(Long id, String name, String description) {
    this.id = id;
    this.name = name;
    this.description = description;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
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
}
