package knowflow.sanjin.modules.extraction.exception;

import knowflow.sanjin.common.error.ErrorCode;

/** 会话没有任何已完成的 active Turn，无法提取（前端应禁用按钮，后端兜底校验）。 */
public class ExtractionNoCompletedMessagesException extends RuntimeException {

  public ExtractionNoCompletedMessagesException(Long conversationId) {
    super("会话 " + conversationId + " 没有可提取的已完成消息");
  }

  public String getErrorCode() {
    return ErrorCode.EXTRACTION_NO_COMPLETED_MESSAGES;
  }
}
