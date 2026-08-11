package knowflow.sanjin.modules.document.controller;

import java.util.List;
import knowflow.sanjin.modules.document.entity.FileMetadata;
import knowflow.sanjin.modules.document.vo.FileMetadataResponse;
import knowflow.sanjin.modules.document.vo.FileUploadResponse;
import knowflow.sanjin.modules.document.vo.KnowledgeItemForFileResponse;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;

/** FileMetadata 与上传响应的显式映射（不使用 MapStruct）。 */
public final class FileMetadataAssembler {

  private FileMetadataAssembler() {}

  public static FileMetadataResponse toResponse(FileMetadata file) {
    FileMetadataResponse r = new FileMetadataResponse();
    r.setId(String.valueOf(file.getId()));
    r.setKnowledgeItemId(String.valueOf(file.getKnowledgeItemId()));
    r.setOriginalFilename(file.getOriginalFilename());
    r.setContentType(file.getContentType());
    r.setDetectedMimeType(file.getDetectedMimeType());
    r.setByteSize(file.getByteSize());
    r.setSha256(file.getSha256());
    r.setStatus(file.getStatus());
    r.setParseStatus(file.getParseStatus());
    r.setParseErrorCode(file.getParseErrorCode());
    r.setParseErrorMessage(file.getParseErrorMessage());
    r.setCreatedAt(file.getCreatedAt());
    return r;
  }

  public static List<FileMetadataResponse> toResponseList(List<FileMetadata> files) {
    return files.stream().map(FileMetadataAssembler::toResponse).toList();
  }

  public static FileUploadResponse toUploadResponse(FileMetadata file, KnowledgeItem item) {
    return toUploadResponse(file, item, false);
  }

  public static FileUploadResponse toUploadResponse(
      FileMetadata file, KnowledgeItem item, boolean duplicate) {
    FileUploadResponse r = new FileUploadResponse();
    r.setFile(toResponse(file));
    r.setItem(toItemResponse(item));
    r.setDuplicate(duplicate);
    return r;
  }

  private static KnowledgeItemForFileResponse toItemResponse(KnowledgeItem item) {
    KnowledgeItemForFileResponse r = new KnowledgeItemForFileResponse();
    r.setId(String.valueOf(item.getId()));
    r.setTitle(item.getTitle());
    r.setSourceType(item.getSourceType());
    r.setIndexStatus(item.getIndexStatus());
    return r;
  }
}
