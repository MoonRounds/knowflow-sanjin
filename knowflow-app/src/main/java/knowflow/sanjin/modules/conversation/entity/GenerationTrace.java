package knowflow.sanjin.modules.conversation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 一次 Generation 的 RAG Trace：与 assistant message 1:1。sources/router/retrieval 以 JSON 快照保存，
 * 供历史消息重放当次检索结果与引用；不含完整 Prompt 或私人正文。
 */
@TableName("generation_trace")
public class GenerationTrace {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long assistantMessageId;

  private Long conversationId;

  private Long ownerId;

  private String ragStatus;

  private String sourcesJson;

  private String routerJson;

  private String retrievalJson;

  @TableField(fill = FieldFill.INSERT)
  private Instant createdAt;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private Instant updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getAssistantMessageId() {
    return assistantMessageId;
  }

  public void setAssistantMessageId(Long assistantMessageId) {
    this.assistantMessageId = assistantMessageId;
  }

  public Long getConversationId() {
    return conversationId;
  }

  public void setConversationId(Long conversationId) {
    this.conversationId = conversationId;
  }

  public Long getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(Long ownerId) {
    this.ownerId = ownerId;
  }

  public String getRagStatus() {
    return ragStatus;
  }

  public void setRagStatus(String ragStatus) {
    this.ragStatus = ragStatus;
  }

  public String getSourcesJson() {
    return sourcesJson;
  }

  public void setSourcesJson(String sourcesJson) {
    this.sourcesJson = sourcesJson;
  }

  public String getRouterJson() {
    return routerJson;
  }

  public void setRouterJson(String routerJson) {
    this.routerJson = routerJson;
  }

  public String getRetrievalJson() {
    return retrievalJson;
  }

  public void setRetrievalJson(String retrievalJson) {
    this.retrievalJson = retrievalJson;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
