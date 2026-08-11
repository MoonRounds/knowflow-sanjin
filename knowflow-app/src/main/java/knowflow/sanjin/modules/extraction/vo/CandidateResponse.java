package knowflow.sanjin.modules.extraction.vo;

/** 候选响应：AI 原值与用户编辑草稿并列返回；BIGINT 序列化为字符串。 */
public class CandidateResponse {

  private String id;
  private String extractionTaskId;
  private String status;
  private String aiTitle;
  private String aiSummary;
  private String aiContent;
  private String[] aiKnowledgeBaseIds;
  private String[] aiTags;
  private String aiReason;
  private String draftTitle;
  private String draftSummary;
  private String draftContent;
  private String[] draftKnowledgeBaseIds;
  private String[] draftTags;
  private Integer rowVersion;
  private String confirmedItemId;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getExtractionTaskId() {
    return extractionTaskId;
  }

  public void setExtractionTaskId(String extractionTaskId) {
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

  public String[] getAiKnowledgeBaseIds() {
    return aiKnowledgeBaseIds;
  }

  public void setAiKnowledgeBaseIds(String[] aiKnowledgeBaseIds) {
    this.aiKnowledgeBaseIds = aiKnowledgeBaseIds;
  }

  public String[] getAiTags() {
    return aiTags;
  }

  public void setAiTags(String[] aiTags) {
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

  public String[] getDraftKnowledgeBaseIds() {
    return draftKnowledgeBaseIds;
  }

  public void setDraftKnowledgeBaseIds(String[] draftKnowledgeBaseIds) {
    this.draftKnowledgeBaseIds = draftKnowledgeBaseIds;
  }

  public String[] getDraftTags() {
    return draftTags;
  }

  public void setDraftTags(String[] draftTags) {
    this.draftTags = draftTags;
  }

  public Integer getRowVersion() {
    return rowVersion;
  }

  public void setRowVersion(Integer rowVersion) {
    this.rowVersion = rowVersion;
  }

  public String getConfirmedItemId() {
    return confirmedItemId;
  }

  public void setConfirmedItemId(String confirmedItemId) {
    this.confirmedItemId = confirmedItemId;
  }
}
