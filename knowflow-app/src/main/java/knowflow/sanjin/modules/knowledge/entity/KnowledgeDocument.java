package knowflow.sanjin.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 可索引知识文档：V1 来源为 Manual Note 与 Upload 文件。
 *
 * <p>{@code contentVersion} 每次正文编辑递增；{@code indexedVersion} 是当前成功索引的版本，旧索引在新版本成功前 继续服务。{@code
 * indexStatus} 与 {@code indexErrorCode/Message} 保存处理状态与错误摘要；{@code rowVersion} 是用户可编辑记录的乐观锁版本（通过
 * If-Match/ETag 传递）。{@code kbId} 是单归属知识库（ADR 0007）；{@code deleted} 是软删标记（统一布尔语义）。
 */
@TableName("knowledge_document")
public class KnowledgeDocument {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long ownerId;

  /** 单归属知识库 id（ADR 0007）：每个文档恰好属于一个知识库。 */
  private Long kbId;

  private String sourceType;

  private String title;

  private String summary;

  private String content;

  private Integer contentVersion;

  private Integer indexedVersion;

  private String indexStatus;

  private String indexErrorCode;

  private String indexErrorMessage;

  /** 软删标记：true 表示已删除，不再可检索（替代旧 status=DELETED）。 */
  private Boolean deleted;

  /** Candidate 来源 id（0..1）：由 Candidate 确认创建的文档记录其来源候选。 */
  private Long candidateId;

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

  public Long getKbId() {
    return kbId;
  }

  public void setKbId(Long kbId) {
    this.kbId = kbId;
  }

  public String getSourceType() {
    return sourceType;
  }

  public void setSourceType(String sourceType) {
    this.sourceType = sourceType;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Integer getContentVersion() {
    return contentVersion;
  }

  public void setContentVersion(Integer contentVersion) {
    this.contentVersion = contentVersion;
  }

  public Integer getIndexedVersion() {
    return indexedVersion;
  }

  public void setIndexedVersion(Integer indexedVersion) {
    this.indexedVersion = indexedVersion;
  }

  public String getIndexStatus() {
    return indexStatus;
  }

  public void setIndexStatus(String indexStatus) {
    this.indexStatus = indexStatus;
  }

  public String getIndexErrorCode() {
    return indexErrorCode;
  }

  public void setIndexErrorCode(String indexErrorCode) {
    this.indexErrorCode = indexErrorCode;
  }

  public String getIndexErrorMessage() {
    return indexErrorMessage;
  }

  public void setIndexErrorMessage(String indexErrorMessage) {
    this.indexErrorMessage = indexErrorMessage;
  }

  public Boolean getDeleted() {
    return deleted;
  }

  public void setDeleted(Boolean deleted) {
    this.deleted = deleted;
  }

  public Long getCandidateId() {
    return candidateId;
  }

  public void setCandidateId(Long candidateId) {
    this.candidateId = candidateId;
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
