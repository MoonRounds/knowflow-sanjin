package knowflow.sanjin.modules.conversation.exception;

/** 会话不存在或属于其他 Owner（越权视为不存在）。 */
public class ConversationNotFoundException extends RuntimeException {

  private final Long id;

  public ConversationNotFoundException(Long id) {
    super("会话不存在: id=" + id);
    this.id = id;
  }

  public Long getId() {
    return id;
  }
}
