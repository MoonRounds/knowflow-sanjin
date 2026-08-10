package knowflow.sanjin.modules.owner.service;

import org.springframework.stereotype.Component;

@Component
public class CurrentOwnerProvider {

  private static final long SYSTEM_OWNER_ID = 1L;

  public long getCurrentOwnerId() {
    return SYSTEM_OWNER_ID;
  }
}
