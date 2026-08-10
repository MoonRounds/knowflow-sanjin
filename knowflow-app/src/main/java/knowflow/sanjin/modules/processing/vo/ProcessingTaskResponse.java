package knowflow.sanjin.modules.processing.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Processing 任务响应：BIGINT id 字符串化；暴露状态、重试与错误摘要供轻量页面展示与 Retry。 */
public class ProcessingTaskResponse {

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String id;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String taskType;

  private String businessKey;

  private String businessId;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String status;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private int retryCount;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private int maxRetries;

  private String failureCode;

  private String lastError;

  private String retryOfTaskId;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private Instant createdAt;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private Instant updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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

  public String getBusinessId() {
    return businessId;
  }

  public void setBusinessId(String businessId) {
    this.businessId = businessId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(int retryCount) {
    this.retryCount = retryCount;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
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

  public String getRetryOfTaskId() {
    return retryOfTaskId;
  }

  public void setRetryOfTaskId(String retryOfTaskId) {
    this.retryOfTaskId = retryOfTaskId;
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
