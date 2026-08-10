package knowflow.sanjin.modules.knowledgebase.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/**
 * 逻辑知识域：同一 Owner 下 normalized name 唯一，可启用/禁用与软删除。
 *
 * <p>{@code rowVersion} 为乐观锁版本；写接口通过 HTTP If-Match/ETag 传递。禁用不等于删除：禁用仅 不进入
 * Router，删除为软删除（deleted=1）且不级联已沉淀的 Item。
 */
@TableName("knowledge_base")
public class KnowledgeBase {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long ownerId;

  private String displayName;

  private String normalizedName;

  private String description;

  private Boolean enabled;

  private Boolean deleted;

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

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getNormalizedName() {
    return normalizedName;
  }

  public void setNormalizedName(String normalizedName) {
    this.normalizedName = normalizedName;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getDeleted() {
    return deleted;
  }

  public void setDeleted(Boolean deleted) {
    this.deleted = deleted;
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
