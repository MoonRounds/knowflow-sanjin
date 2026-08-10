package knowflow.sanjin.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** KnowledgeItem 与 KnowledgeBase 多对多关系；软删（deleted=1），删除后重新关联可复用原行。 */
@TableName("knowledge_base_item")
public class KnowledgeBaseItem {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long knowledgeBaseId;

  private Long knowledgeItemId;

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

  public Long getKnowledgeBaseId() {
    return knowledgeBaseId;
  }

  public void setKnowledgeBaseId(Long knowledgeBaseId) {
    this.knowledgeBaseId = knowledgeBaseId;
  }

  public Long getKnowledgeItemId() {
    return knowledgeItemId;
  }

  public void setKnowledgeItemId(Long knowledgeItemId) {
    this.knowledgeItemId = knowledgeItemId;
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
