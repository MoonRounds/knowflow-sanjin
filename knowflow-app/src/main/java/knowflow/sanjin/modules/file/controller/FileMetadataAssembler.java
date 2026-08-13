package knowflow.sanjin.modules.file.controller;

import java.util.List;
import knowflow.sanjin.modules.file.entity.FileMetadata;
import knowflow.sanjin.modules.file.vo.FileMetadataResponse;
import knowflow.sanjin.modules.file.vo.FileUploadResponse;
import knowflow.sanjin.modules.file.vo.KnowledgeDocumentForFileResponse;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;

/** FileMetadata 与上传响应的显式映射（不使用 MapStruct）。 */
public final class FileMetadataAssembler {

  private FileMetadataAssembler() {}

  public static FileMetadataResponse toResponse(FileMetadata file) {
    FileMetadataResponse r = new FileMetadataResponse();
    r.setId(String.valueOf(file.getId()));
    r.setKnowledgeDocumentId(String.valueOf(file.getKnowledgeDocumentId()));
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

  public static FileUploadResponse toUploadResponse(FileMetadata file, KnowledgeDocument item) {
    return toUploadResponse(file, item, false);
  }

  public static FileUploadResponse toUploadResponse(
      FileMetadata file, KnowledgeDocument item, boolean duplicate) {
    FileUploadResponse r = new FileUploadResponse();
    r.setFile(toResponse(file));
    r.setItem(toDocumentResponse(item));
    r.setDuplicate(duplicate);
    return r;
  }

  private static KnowledgeDocumentForFileResponse toDocumentResponse(KnowledgeDocument item) {
    KnowledgeDocumentForFileResponse r = new KnowledgeDocumentForFileResponse();
    r.setId(String.valueOf(item.getId()));
    r.setTitle(item.getTitle());
    r.setSourceType(item.getSourceType());
    r.setIndexStatus(item.getIndexStatus());
    return r;
  }
}
