package knowflow.sanjin.modules.modelconfig.exception;

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
