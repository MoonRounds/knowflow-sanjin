package knowflow.sanjin.modules.modelconfig.exception;

/** 同一配置不能同时被设为默认聊天模型与 Utility 模型，否则角色互相冲突。 */
public class ModelConfigRoleConflictException extends RuntimeException {

  private final Long id;

  public ModelConfigRoleConflictException(Long id) {
    super("模型配置 " + id + " 不能同时作为默认聊天模型与 Utility 模型");
    this.id = id;
  }

  public Long getId() {
    return id;
  }
}
