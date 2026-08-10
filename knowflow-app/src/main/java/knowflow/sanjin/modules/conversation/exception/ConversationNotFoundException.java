package knowflow.sanjin.modules.conversation.exception;

public class ConversationNotFoundException extends RuntimeException {

  private final Long id;

  public ConversationNotFoundException(Long id) {
    super("Conversation not found: id=" + id);
    this.id = id;
  }

  public Long getId() {
    return id;
  }
}
