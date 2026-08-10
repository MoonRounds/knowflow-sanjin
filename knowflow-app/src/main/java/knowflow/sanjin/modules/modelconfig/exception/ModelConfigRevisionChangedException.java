package knowflow.sanjin.modules.modelconfig.exception;

public class ModelConfigRevisionChangedException extends RuntimeException {

  private final Long id;

  public ModelConfigRevisionChangedException(Long id) {
    super("ModelConfig " + id + " changed while its capability test was running");
    this.id = id;
  }

  public Long getId() {
    return id;
  }
}
