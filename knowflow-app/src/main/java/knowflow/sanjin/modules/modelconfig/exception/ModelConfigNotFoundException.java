package knowflow.sanjin.modules.modelconfig.exception;

/** 配置不存在或属于其他 Owner（越权视为不存在）。 */
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
