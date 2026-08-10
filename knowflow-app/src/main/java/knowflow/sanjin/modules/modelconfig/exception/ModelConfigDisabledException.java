package knowflow.sanjin.modules.modelconfig.exception;

/** 被禁用（enabled=false）的配置不能用于 Generation 或 Owner 默认设置。 */
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
