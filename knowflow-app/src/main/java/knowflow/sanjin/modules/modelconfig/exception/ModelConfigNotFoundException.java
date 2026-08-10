package knowflow.sanjin.modules.modelconfig.exception;

public class ModelConfigNotFoundException extends RuntimeException {

  private final Long id;

  public ModelConfigNotFoundException(Long id) {
    super("ModelConfig not found: " + id);
    this.id = id;
  }

  public Long getId() {
    return id;
  }
}
