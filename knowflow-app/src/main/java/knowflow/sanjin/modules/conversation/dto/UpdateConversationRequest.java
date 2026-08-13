package knowflow.sanjin.modules.conversation.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/** 更新会话请求：字段未传则保持不变；rowVersion 用于乐观锁校验。 */
public class UpdateConversationRequest {

  @Size(max = 200)
  private String title;

  private String defaultModelConfigId;

  private Long rowVersion;

  private List<String> knowledgeBaseIds;

  private boolean knowledgeBaseIdsPresent;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDefaultModelConfigId() {
    return defaultModelConfigId;
  }

  public void setDefaultModelConfigId(String defaultModelConfigId) {
    this.defaultModelConfigId = defaultModelConfigId;
  }

  public Long getRowVersion() {
    return rowVersion;
  }

  public void setRowVersion(Long rowVersion) {
    this.rowVersion = rowVersion;
  }

  public List<String> getKnowledgeBaseIds() {
    return knowledgeBaseIds;
  }

  public void setKnowledgeBaseIds(List<String> knowledgeBaseIds) {
    this.knowledgeBaseIds = knowledgeBaseIds;
    this.knowledgeBaseIdsPresent = true;
  }

  public boolean knowledgeBaseIdsWasSet() {
    return knowledgeBaseIdsPresent;
  }
}
