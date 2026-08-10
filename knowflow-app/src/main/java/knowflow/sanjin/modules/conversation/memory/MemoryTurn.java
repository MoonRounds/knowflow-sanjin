package knowflow.sanjin.modules.conversation.memory;

/**
 * Memory 中的单条消息：USER 或 ASSISTANT 角色 + 正文。
 *
 * <p>仅保存上下文所需字段；不保存消息 id、model、usage 等（MySQL 是事实源，Memory 是投影）。
 */
public class MemoryTurn {

  public static final String ROLE_USER = "USER";
  public static final String ROLE_ASSISTANT = "ASSISTANT";

  private String role;
  private String content;

  public MemoryTurn() {}

  public MemoryTurn(String role, String content) {
    this.role = role;
    this.content = content;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }
}
