package knowflow.sanjin.modules.extraction.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 知识候选：AI 提取结果与人工审核记录。
 *
 * <p>{@code ai_*} 是 AI 原始提取结果，写入后不可变快照；{@code draft_*} 是用户可编辑草稿，初始化时复制 AI 原值， 确认时完整读取草稿字段 （未改动的字段即
 * AI 原值，无「回退」语义）。状态迁移：PENDING → CONFIRMED / REJECTED； REJECTED → PENDING（撤销拒绝，唯一回退）； CONFIRMED
 * 是终态，不得再次生成第二个 Document。每个 Candidate 通过 {@code knowledge_document.candidate_id} 至多创建一个
 * Document，由唯一约束保证。 KB 归属为单值（ADR 0007）：{@code ai_knowledge_base_id} / {@code
 * draft_knowledge_base_id}。
 */
@TableName("knowledge_candidate")
public class KnowledgeCandidate {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long ownerId;

  private Long extractionTaskId;

  private String status;

  private String aiTitle;

  private String aiSummary;

  private String aiContent;

  private String aiKnowledgeBaseId;

  private String aiTags;

  private String aiReason;

  private String draftTitle;

  private String draftSummary;

  private String draftContent;

  private String draftKnowledgeBaseId;

  private String draftTags;

  private Instant draftUpdatedAt;

  private Instant rejectedAt;

  private Instant confirmedAt;

  private Integer rowVersion;

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

  public Long getExtractionTaskId() {
    return extractionTaskId;
  }

  public void setExtractionTaskId(Long extractionTaskId) {
    this.extractionTaskId = extractionTaskId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getAiTitle() {
    return aiTitle;
  }

  public void setAiTitle(String aiTitle) {
    this.aiTitle = aiTitle;
  }

  public String getAiSummary() {
    return aiSummary;
  }

  public void setAiSummary(String aiSummary) {
    this.aiSummary = aiSummary;
  }

  public String getAiContent() {
    return aiContent;
  }

  public void setAiContent(String aiContent) {
    this.aiContent = aiContent;
  }

  public String getAiKnowledgeBaseId() {
    return aiKnowledgeBaseId;
  }

  public void setAiKnowledgeBaseId(String aiKnowledgeBaseId) {
    this.aiKnowledgeBaseId = aiKnowledgeBaseId;
  }

  public String getAiTags() {
    return aiTags;
  }

  public void setAiTags(String aiTags) {
    this.aiTags = aiTags;
  }

  public String getAiReason() {
    return aiReason;
  }

  public void setAiReason(String aiReason) {
    this.aiReason = aiReason;
  }

  public String getDraftTitle() {
    return draftTitle;
  }

  public void setDraftTitle(String draftTitle) {
    this.draftTitle = draftTitle;
  }

  public String getDraftSummary() {
    return draftSummary;
  }

  public void setDraftSummary(String draftSummary) {
    this.draftSummary = draftSummary;
  }

  public String getDraftContent() {
    return draftContent;
  }

  public void setDraftContent(String draftContent) {
    this.draftContent = draftContent;
  }

  public String getDraftKnowledgeBaseId() {
    return draftKnowledgeBaseId;
  }

  public void setDraftKnowledgeBaseId(String draftKnowledgeBaseId) {
    this.draftKnowledgeBaseId = draftKnowledgeBaseId;
  }

  public String getDraftTags() {
    return draftTags;
  }

  public void setDraftTags(String draftTags) {
    this.draftTags = draftTags;
  }

  public Instant getDraftUpdatedAt() {
    return draftUpdatedAt;
  }

  public void setDraftUpdatedAt(Instant draftUpdatedAt) {
    this.draftUpdatedAt = draftUpdatedAt;
  }

  public Instant getRejectedAt() {
    return rejectedAt;
  }

  public void setRejectedAt(Instant rejectedAt) {
    this.rejectedAt = rejectedAt;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
  }

  public void setConfirmedAt(Instant confirmedAt) {
    this.confirmedAt = confirmedAt;
  }

  public Integer getRowVersion() {
    return rowVersion;
  }

  public void setRowVersion(Integer rowVersion) {
    this.rowVersion = rowVersion;
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
