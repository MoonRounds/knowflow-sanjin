package knowflow.sanjin.modules.conversation.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis 中保存的 Chat Memory 投影：schema 版本 + 角色/内容消息列表。
 *
 * <p>只含 {@code role} 与 {@code content} 两类字段，不含 model、usage 等非上下文信息。版本字段供未来格式 演进时兼容读取或触发重建。
 */
public class MemorySnapshot {

  private int schemaVersion;
  private List<MemoryTurn> messages = new ArrayList<>();

  public MemorySnapshot() {}

  public MemorySnapshot(int schemaVersion, List<MemoryTurn> messages) {
    this.schemaVersion = schemaVersion;
    this.messages = messages;
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(int schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public List<MemoryTurn> getMessages() {
    return messages;
  }

  public void setMessages(List<MemoryTurn> messages) {
    this.messages = messages;
  }
}
