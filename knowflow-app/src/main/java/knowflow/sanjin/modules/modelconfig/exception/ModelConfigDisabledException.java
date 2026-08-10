package knowflow.sanjin.modules.modelconfig.exception;

public class ModelConfigDisabledException extends RuntimeException {

  private final Long id;

  public ModelConfigDisabledException(Long id) {
    super("ModelConfig is disabled: " + id);
    this.id = id;
  }

  public Long getId() {
    return id;
  }
}
