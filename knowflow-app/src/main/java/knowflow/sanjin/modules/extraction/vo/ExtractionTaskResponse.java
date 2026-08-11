package knowflow.sanjin.modules.extraction.vo;

/** 提取任务快照响应：BIGINT 序列化为字符串。 */
public class ExtractionTaskResponse {

  private String id;
  private String conversationId;
  private String cutoffMessageId;
  private String extractionProfile;
  private Integer profileVersion;
  private String utilityRevisionId;
  private String processingTaskId;
  private Integer inputCharCount;
  private Integer candidateCount;
  private String status;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getConversationId() {
    return conversationId;
  }

  public void setConversationId(String conversationId) {
    this.conversationId = conversationId;
  }

  public String getCutoffMessageId() {
    return cutoffMessageId;
  }

  public void setCutoffMessageId(String cutoffMessageId) {
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

  public String getUtilityRevisionId() {
    return utilityRevisionId;
  }

  public void setUtilityRevisionId(String utilityRevisionId) {
    this.utilityRevisionId = utilityRevisionId;
  }

  public String getProcessingTaskId() {
    return processingTaskId;
  }

  public void setProcessingTaskId(String processingTaskId) {
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
