package knowflow.sanjin.modules.extraction.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 提取任务快照：保存一次提取的固定范围、提取 profile 版本、锁定 Utility Revision 与 结果统计。
 *
 * <p>任务统一状态机在 {@code processing_task}（PENDING/PROCESSING/SUCCEEDED/FAILED），本表不重复保存状态； {@code
 * uk_kext_dedup} 唯一约束保证相同 owner/conversation/cutoff/profile/revision 只创建一个任务（幂等键）。 任务只保存范围与身份引用，
 * 不复制整份 Conversation 正文（正文按 cutoff 从 MySQL 实时读取）。
 */
@TableName("knowledge_extraction_task")
public class KnowledgeExtractionTask {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long ownerId;

  private Long conversationId;

  private Long cutoffMessageId;

  private String extractionProfile;

  private Integer profileVersion;

  private Long utilityRevisionId;

  private Long processingTaskId;

  private Integer inputCharCount;

  private Integer candidateCount;

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

  public Long getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(Long ownerId) {
    this.ownerId = ownerId;
  }

  public Long getConversationId() {
    return conversationId;
  }

  public void setConversationId(Long conversationId) {
    this.conversationId = conversationId;
  }

  public Long getCutoffMessageId() {
    return cutoffMessageId;
  }

  public void setCutoffMessageId(Long cutoffMessageId) {
    this.cutoffMessageId = cutoffMessageId;
  }

  public String getExtractionProfile() {
    return extractionProfile;
  }

  public void setExtractionProfile(String extractionProfile) {
    this.extractionProfile = extractionProfile;
  }

  public Integer getProfileVersion() {
    return profileVersion;
  }

  public void setProfileVersion(Integer profileVersion) {
    this.profileVersion = profileVersion;
  }

  public Long getUtilityRevisionId() {
    return utilityRevisionId;
  }

  public void setUtilityRevisionId(Long utilityRevisionId) {
    this.utilityRevisionId = utilityRevisionId;
  }

  public Long getProcessingTaskId() {
    return processingTaskId;
  }

  public void setProcessingTaskId(Long processingTaskId) {
    this.processingTaskId = processingTaskId;
  }

  public Integer getInputCharCount() {
    return inputCharCount;
  }

  public void setInputCharCount(Integer inputCharCount) {
    this.inputCharCount = inputCharCount;
  }

  public Integer getCandidateCount() {
    return candidateCount;
  }

  public void setCandidateCount(Integer candidateCount) {
    this.candidateCount = candidateCount;
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
