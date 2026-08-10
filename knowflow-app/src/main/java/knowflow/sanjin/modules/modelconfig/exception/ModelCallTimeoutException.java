package knowflow.sanjin.modules.modelconfig.exception;

/** 模型调用在总体超时内未完成。 */
public class ModelCallTimeoutException extends RuntimeException {

  public ModelCallTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
