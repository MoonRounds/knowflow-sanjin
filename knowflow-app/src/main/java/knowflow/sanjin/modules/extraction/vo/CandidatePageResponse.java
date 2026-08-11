package knowflow.sanjin.modules.extraction.vo;

import java.util.List;

/** 候选分页响应。 */
public class CandidatePageResponse {

  private long total;
  private long page;
  private long size;
  private List<CandidateResponse> items;

  public CandidatePageResponse(long total, long page, long size, List<CandidateResponse> items) {
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

  public List<CandidateResponse> getItems() {
    return items;
  }

  public void setItems(List<CandidateResponse> items) {
    this.items = items;
  }
}
