package knowflow.sanjin.modules.rag.dto;

import java.util.List;

/** 一次 RAG 路由阶段的完整输入与结果（供 GenerationTrace 持久化，仅诊断用途）。 */
public class RouterTrace {

  private String mode;

  /** 提供给 Router 的可路由 KnowledgeBase 目录（仅 id/name/description）。 */
  private List<RoutableKnowledgeBase> catalog;

  private boolean routerCalled;

  private RouterResult result;

  /** 是否发生了受控修复。 */
  private boolean fixed;

  /** Router 失败时的稳定错误说明（不含敏感信息）。 */
  private String failure;

  /** 目录为空 / Utility 不可用，未调用 Router。 */
  public static RouterTrace catalogOnly(List<RoutableKnowledgeBase> catalog) {
    RouterTrace t = new RouterTrace();
    t.setCatalog(catalog);
    t.setRouterCalled(false);
    return t;
  }

  /** 已尝试调用但失败（解析修复后仍失败 / Provider 异常）。 */
  public static RouterTrace failed(List<RoutableKnowledgeBase> catalog, String failure) {
    RouterTrace t = new RouterTrace();
    t.setCatalog(catalog);
    t.setRouterCalled(false);
    t.setFailure(failure);
    return t;
  }

  public List<RoutableKnowledgeBase> getCatalog() {
    return catalog;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public void setCatalog(List<RoutableKnowledgeBase> catalog) {
    this.catalog = catalog;
  }

  public boolean isRouterCalled() {
    return routerCalled;
  }

  public void setRouterCalled(boolean routerCalled) {
    this.routerCalled = routerCalled;
  }

  public RouterResult getResult() {
    return result;
  }

  public void setResult(RouterResult result) {
    this.result = result;
  }

  public boolean isFixed() {
    return fixed;
  }

  public void setFixed(boolean fixed) {
    this.fixed = fixed;
  }

  public String getFailure() {
    return failure;
  }

  public void setFailure(String failure) {
    this.failure = failure;
  }
}
