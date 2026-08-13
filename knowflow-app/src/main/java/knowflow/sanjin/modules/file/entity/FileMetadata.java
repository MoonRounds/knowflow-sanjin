package knowflow.sanjin.modules.file.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 上传原文件元数据：与 KnowledgeDocument 一对一（DECISIONS §14）。原文件字节不修改，存储键由系统生成， 用户文件名仅作展示。去重身份为 ownerId + 检测
 * MIME + 原始内容 SHA-256。
 */
@TableName("file_metadata")
public class FileMetadata {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long ownerId;

  private Long knowledgeDocumentId;

  /** 系统生成的受控存储键（不含用户文件名），用于定位本地原文件。 */
  private String storageKey;

  private String originalFilename;

  private String contentType;

  /** Tika 检测出的标准化 MIME（与去重键一致）。 */
  private String detectedMimeType;

  private Long byteSize;

  private String sha256;

  private String status;

  private String parseStatus;

  private String parseErrorCode;

  private String parseErrorMessage;

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

  public Long getKnowledgeDocumentId() {
    return knowledgeDocumentId;
  }

  public void setKnowledgeDocumentId(Long knowledgeDocumentId) {
    this.knowledgeDocumentId = knowledgeDocumentId;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public void setStorageKey(String storageKey) {
    this.storageKey = storageKey;
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

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
