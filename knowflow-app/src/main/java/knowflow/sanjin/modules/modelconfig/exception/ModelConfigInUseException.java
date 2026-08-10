package knowflow.sanjin.modules.modelconfig.exception;

/** 被引用的配置不能删除；删除会导致 Owner 默认 Chat/Utility 设置悬空。 */
public class ModelConfigInUseException extends RuntimeException {

  public ModelConfigInUseException(String message) {
    super(message);
  }
}
