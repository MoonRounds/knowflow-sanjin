package knowflow.sanjin.modules.conversation.vo;

import java.time.Instant;

public class MessageResponse {

  private String id;
  private String conversationId;
  private String role;
  private String content;
  private String replyToMessageId;
  private String generationStatus;
  private boolean active;
  private String modelConfigId;
  private Integer revisionNo;
  private String modelName;
  private String providerName;
  private String errorCode;
  private TokenUsage usage;
  private Instant createdAt;
  private Instant updatedAt;

  public static class TokenUsage {
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    public Integer getPromptTokens() {
      return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
      this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
      return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
      this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
      return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
      this.totalTokens = totalTokens;
    }
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getConversationId() {
    return conversationId;
  }

  public void setConversationId(String conversationId) {
    this.conversationId = conversationId;
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

  public String getReplyToMessageId() {
    return replyToMessageId;
  }

  public void setReplyToMessageId(String replyToMessageId) {
    this.replyToMessageId = replyToMessageId;
  }

  public String getGenerationStatus() {
    return generationStatus;
  }

  public void setGenerationStatus(String generationStatus) {
    this.generationStatus = generationStatus;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public String getModelConfigId() {
    return modelConfigId;
  }

  public void setModelConfigId(String modelConfigId) {
    this.modelConfigId = modelConfigId;
  }

  public Integer getRevisionNo() {
    return revisionNo;
  }

  public void setRevisionNo(Integer revisionNo) {
    this.revisionNo = revisionNo;
  }

  public String getModelName() {
    return modelName;
  }

  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  public String getProviderName() {
    return providerName;
  }

  public void setProviderName(String providerName) {
    this.providerName = providerName;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  public TokenUsage getUsage() {
    return usage;
  }

  public void setUsage(TokenUsage usage) {
    this.usage = usage;
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
