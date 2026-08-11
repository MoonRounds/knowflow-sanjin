package knowflow.sanjin.modules.document.vo;

import java.time.Instant;

/** FileMetadata 视图：展示文件名/MIME/大小/SHA/状态，不暴露本地真实路径。 */
public class FileMetadataResponse {

  private String id;
  private String knowledgeItemId;
  private String originalFilename;
  private String contentType;
  private String detectedMimeType;
  private Long byteSize;
  private String sha256;
  private String status;
  private String parseStatus;
  private String parseErrorCode;
  private String parseErrorMessage;
  private Instant createdAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getKnowledgeItemId() {
    return knowledgeItemId;
  }

  public void setKnowledgeItemId(String knowledgeItemId) {
    this.knowledgeItemId = knowledgeItemId;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public void setOriginalFilename(String originalFilename) {
    this.originalFilename = originalFilename;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public String getDetectedMimeType() {
    return detectedMimeType;
  }

  public void setDetectedMimeType(String detectedMimeType) {
    this.detectedMimeType = detectedMimeType;
  }

  public Long getByteSize() {
    return byteSize;
  }

  public void setByteSize(Long byteSize) {
    this.byteSize = byteSize;
  }

  public String getSha256() {
    return sha256;
  }

  public void setSha256(String sha256) {
    this.sha256 = sha256;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getParseStatus() {
    return parseStatus;
  }

  public void setParseStatus(String parseStatus) {
    this.parseStatus = parseStatus;
  }

  public String getParseErrorCode() {
    return parseErrorCode;
  }

  public void setParseErrorCode(String parseErrorCode) {
    this.parseErrorCode = parseErrorCode;
  }

  public String getParseErrorMessage() {
    return parseErrorMessage;
  }

  public void setParseErrorMessage(String parseErrorMessage) {
    this.parseErrorMessage = parseErrorMessage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
