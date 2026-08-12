package knowflow.sanjin.modules.knowledgebase.exception;

/** 同一 Owner 下 normalized name 唯一冲突（由数据库唯一约束触发）。 */
public class KnowledgeBaseNameConflictException extends RuntimeException {

  private final String name;

  public KnowledgeBaseNameConflictException(String name) {
    super("已存在同名知识库: " + name);
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
