package knowflow.sanjin.modules.modelconfig.exception;

/** 能力测试期间 current Revision 被切换（条件更新受影响行数为 0），测试结果作废。 */
public class ModelConfigRevisionChangedException extends RuntimeException {

  private final Long id;

  public ModelConfigRevisionChangedException(Long id) {
    super("模型配置 " + id + " 在能力测试期间已被切换");
    this.id = id;
  }

  public Long getId() {
    return id;
  }
}
