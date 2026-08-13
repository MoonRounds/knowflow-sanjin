package knowflow.sanjin.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** KnowledgeDocument 与 Tag 多对多关系；软删（deleted=1）。 */
@TableName("knowledge_document_tag")
public class KnowledgeDocumentTag {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long knowledgeDocumentId;

  private Long tagId;

  private Long ownerId;

  private Boolean deleted;

  @TableField(fill = FieldFill.INSERT)
  private Instant createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getKnowledgeDocumentId() {
    return knowledgeDocumentId;
  }

  public void setKnowledgeDocumentId(Long knowledgeDocumentId) {
    this.knowledgeDocumentId = knowledgeDocumentId;
  }

  public Long getTagId() {
    return tagId;
  }

  public void setTagId(Long tagId) {
    this.tagId = tagId;
  }

  public Long getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(Long ownerId) {
    this.ownerId = ownerId;
  }

  public Boolean getDeleted() {
    return deleted;
  }

  public void setDeleted(Boolean deleted) {
    this.deleted = deleted;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
