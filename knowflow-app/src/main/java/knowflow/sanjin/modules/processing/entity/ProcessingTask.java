package knowflow.sanjin.modules.processing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 异步任务事实源兼轻量 Outbox：业务事务内写入，提交后发布消息。
 *
 * <p>状态仅 {@code PENDING / PROCESSING / SUCCEEDED / FAILED}。{@code businessKey} 形如 {@code
 * KNOWLEDGE_ITEM:<contentVersion>}，{@code activeFlag} 生成列使活动状态（PENDING/PROCESSING）按 (taskType,
 * businessKey) 唯一。{@code retryOfTaskId} 记录手动重试的来源失败任务；重复重试复用同一 businessKey，靠活动状态唯一约束去重。
 */
@TableName("processing_task")
public class ProcessingTask {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long ownerId;

  private String taskType;

  private String businessKey;

  private Long businessId;

  private String status;

  private Integer retryCount;

  private Integer maxRetries;

  private String failureCode;

  private String lastError;

  private Long retryOfTaskId;

  private String payload;

  private Integer attemptedDeliveries;

  private Instant lastDeliveryAt;

  private Instant startedAt;

  private Instant finishedAt;

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

  public String getTaskType() {
    return taskType;
  }

  public void setTaskType(String taskType) {
    this.taskType = taskType;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(String businessKey) {
    this.businessKey = businessKey;
  }

  public Long getBusinessId() {
    return businessId;
  }

  public void setBusinessId(Long businessId) {
    this.businessId = businessId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(Integer retryCount) {
    this.retryCount = retryCount;
  }

  public Integer getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(Integer maxRetries) {
    this.maxRetries = maxRetries;
  }

  public String getFailureCode() {
    return failureCode;
  }

  public void setFailureCode(String failureCode) {
    this.failureCode = failureCode;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public Long getRetryOfTaskId() {
    return retryOfTaskId;
  }

  public void setRetryOfTaskId(Long retryOfTaskId) {
    this.retryOfTaskId = retryOfTaskId;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public Integer getAttemptedDeliveries() {
    return attemptedDeliveries;
  }

  public void setAttemptedDeliveries(Integer attemptedDeliveries) {
    this.attemptedDeliveries = attemptedDeliveries;
  }

  public Instant getLastDeliveryAt() {
    return lastDeliveryAt;
  }

  public void setLastDeliveryAt(Instant lastDeliveryAt) {
    this.lastDeliveryAt = lastDeliveryAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
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
