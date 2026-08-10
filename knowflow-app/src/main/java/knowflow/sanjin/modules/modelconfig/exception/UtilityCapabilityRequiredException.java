package knowflow.sanjin.modules.modelconfig.exception;

/** 用作 Utility Model 前必须通过结构化输出能力测试（Router/Candidate Schema）。 */
public class UtilityCapabilityRequiredException extends RuntimeException {

  private final Long id;

  public UtilityCapabilityRequiredException(Long id) {
    super("ModelConfig " + id + " current revision has not passed the Utility capability test");
    this.id = id;
  }

  public Long getId() {
    return id;
  }
}
