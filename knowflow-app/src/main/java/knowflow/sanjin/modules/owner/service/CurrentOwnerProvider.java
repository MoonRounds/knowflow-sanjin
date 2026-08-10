package knowflow.sanjin.modules.owner.service;

import org.springframework.stereotype.Component;

/**
 * 当前 Owner 提供者：V1 无认证，统一返回固定 System Owner id=1。
 *
 * <p>Controller 不接受客户端传入 userId；所有资源访问都必须经过本 Provider 的 owner 边界。
 */
@Component
public class CurrentOwnerProvider {

  private static final long SYSTEM_OWNER_ID = 1L;

  public long getCurrentOwnerId() {
    return SYSTEM_OWNER_ID;
  }
}
