package knowflow.sanjin.modules.processing.exception;

/** ProcessingTask 不存在或不可访问（owner 越权视为不存在）。 */
public class ProcessingTaskNotFoundException extends RuntimeException {

  private final Long id;

  public ProcessingTaskNotFoundException(Long id) {
    super("ProcessingTask not found: " + id);
    this.id = id;
  }

  public Long getId() {
    return id;
  }
}
