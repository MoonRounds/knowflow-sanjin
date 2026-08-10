package knowflow.sanjin.modules.conversation.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/**
 * 聊天消息：User 为轮起点；Assistant 为 Generation Attempt。
 *
 * <p>{@code generationStatus} 仅 Assistant 消息有值：GENERATING / COMPLETED / FAILED / CANCELLED。 {@code
 * isActive} 为 1 表示该 Assistant attempt 是当前轮的有效回答（唯一 active completed answer）。
 */
@TableName("chat_message")
public class ChatMessage {

  public static final String ROLE_USER = "USER";
  public static final String ROLE_ASSISTANT = "ASSISTANT";

  public static final String GENERATING = "GENERATING";
  public static final String COMPLETED = "COMPLETED";
  public static final String FAILED = "FAILED";
  public static final String CANCELLED = "CANCELLED";

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long conversationId;

  private Long ownerId;

  private String role;

  /** 会话内单调递增序号，充当历史游标。 */
  private Long sequence;

  private String content;

  private Long replyToMessageId;

  private String clientMessageId;

  private String generationStatus;

  private Boolean isActive;

  private Long modelConfigId;

  private Integer revisionNo;

  private String modelName;

  private String providerName;

  private Double temperature;

  private Integer maxOutputTokens;

  private Integer usagePromptTokens;

  private Integer usageCompletionTokens;

  private Integer usageTotalTokens;

  private String errorCode;

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

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public Long getSequence() {
    return sequence;
  }

  public void setSequence(Long sequence) {
    this.sequence = sequence;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Long getReplyToMessageId() {
    return replyToMessageId;
  }

  public void setReplyToMessageId(Long replyToMessageId) {
    this.replyToMessageId = replyToMessageId;
  }

  public String getClientMessageId() {
    return clientMessageId;
  }

  public void setClientMessageId(String clientMessageId) {
    this.clientMessageId = clientMessageId;
  }

  public String getGenerationStatus() {
    return generationStatus;
  }

  public void setGenerationStatus(String generationStatus) {
    this.generationStatus = generationStatus;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean isActive) {
    this.isActive = isActive;
  }

  public Long getModelConfigId() {
    return modelConfigId;
  }

  public void setModelConfigId(Long modelConfigId) {
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

  public Double getTemperature() {
    return temperature;
  }

  public void setTemperature(Double temperature) {
    this.temperature = temperature;
  }

  public Integer getMaxOutputTokens() {
    return maxOutputTokens;
  }

  public void setMaxOutputTokens(Integer maxOutputTokens) {
    this.maxOutputTokens = maxOutputTokens;
  }

  public Integer getUsagePromptTokens() {
    return usagePromptTokens;
  }

  public void setUsagePromptTokens(Integer usagePromptTokens) {
    this.usagePromptTokens = usagePromptTokens;
  }

  public Integer getUsageCompletionTokens() {
    return usageCompletionTokens;
  }

  public void setUsageCompletionTokens(Integer usageCompletionTokens) {
    this.usageCompletionTokens = usageCompletionTokens;
  }

  public Integer getUsageTotalTokens() {
    return usageTotalTokens;
  }

  public void setUsageTotalTokens(Integer usageTotalTokens) {
    this.usageTotalTokens = usageTotalTokens;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
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
