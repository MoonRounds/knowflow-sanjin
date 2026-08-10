package knowflow.sanjin.modules.owner.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/**
 * 用户表实体：V1 单用户系统，Flyway 只初始化 id=1 的 System Owner。
 *
 * <p>只含展示名称、状态与审计时间，无密码、角色或认证字段（登录/安全属于后续 Phase）。
 */
@TableName("app_user")
public class AppUser {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;

  private String status;

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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
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
