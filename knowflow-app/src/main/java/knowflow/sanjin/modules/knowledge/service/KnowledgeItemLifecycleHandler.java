package knowflow.sanjin.modules.knowledge.service;

/**
 * KnowledgeItem 生命周期回调：跨模块副作用（如 Upload 原文件与 FileMetadata 清理）在此接缝挂接。
 *
 * <p>接口定义在 knowledge 模块内，document 等模块实现，避免 knowledge→document 依赖造成循环。
 */
public interface KnowledgeItemLifecycleHandler {

  /** Item 软删后调用（同步，与业务事务同线程）；实现应保证自身失败不阻塞主流程。 */
  void onItemSoftDeleted(Long itemId, long ownerId);
}
