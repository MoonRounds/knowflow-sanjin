package knowflow.sanjin.modules.modelconfig.exception;

/** 用作 Utility Model 前必须通过结构化输出能力测试（Router/Candidate Schema）。 */
public class UtilityCapabilityRequiredException extends RuntimeException {

  private final Long id;

  public UtilityCapabilityRequiredException(Long id) {
    super("模型配置 " + id + " 的当前版本未通过 Utility 能力测试");
    this.id = id;
  }

  public Long getId() {
    return id;
  }
}
