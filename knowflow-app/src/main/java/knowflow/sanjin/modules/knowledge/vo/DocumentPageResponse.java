package knowflow.sanjin.modules.knowledge.vo;

import java.util.List;

/** 文档分页响应：承接 candidate 分页先例（page/size + total + items）。 */
public class DocumentPageResponse {

  private long total;
  private long page;
  private long size;
  private List<KnowledgeDocumentSummaryResponse> items;

  public DocumentPageResponse(
      long total, long page, long size, List<KnowledgeDocumentSummaryResponse> items) {
    this.total = total;
    this.page = page;
    this.size = size;
    this.items = items;
  }

  public long getTotal() {
    return total;
  }

  public void setTotal(long total) {
    this.total = total;
  }

  public long getPage() {
    return page;
  }

  public void setPage(long page) {
    this.page = page;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }

  public List<KnowledgeDocumentSummaryResponse> getItems() {
    return items;
  }

  public void setItems(List<KnowledgeDocumentSummaryResponse> items) {
    this.items = items;
  }
}
