package knowflow.sanjin.modules.extraction.exception;

import knowflow.sanjin.common.error.ErrorCode;

/** 提取输入超过预算：同步校验拒绝，不创建任务、不调用 LLM（DECISIONS §11）。 */
public class ExtractionInputOverBudgetException extends RuntimeException {

  public ExtractionInputOverBudgetException(int inputChars, int maxChars) {
    super("Extraction input " + inputChars + " chars exceeds the limit of " + maxChars + " chars");
  }

  public String getErrorCode() {
    return ErrorCode.EXTRACTION_INPUT_OVER_BUDGET;
  }
}
