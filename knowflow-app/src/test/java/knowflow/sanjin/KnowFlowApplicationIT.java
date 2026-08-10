package knowflow.sanjin;

import static org.assertj.core.api.Assertions.assertThat;

import knowflow.sanjin.common.controller.HealthController;
import knowflow.sanjin.modules.knowledgebase.controller.KnowledgeBaseController;
import knowflow.sanjin.modules.knowledgebase.mapper.KnowledgeBaseMapper;
import knowflow.sanjin.modules.modelconfig.controller.ModelConfigController;
import knowflow.sanjin.modules.modelconfig.mapper.ModelConfigMapper;
import knowflow.sanjin.testinfra.MySQLTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** 应用上下文冒烟测试：验证关键 Web 组件与持久化 Mapper 能被装配到 Spring 容器。 */
@SpringBootTest
class KnowFlowApplicationIT extends MySQLTestBase {

  @Autowired private ApplicationContext applicationContext;

  @Test
  void contextLoadsAndDiscoversWebAndPersistenceComponents() {
    assertThat(applicationContext.getBean(HealthController.class)).isNotNull();
    assertThat(applicationContext.getBean(KnowledgeBaseController.class)).isNotNull();
    assertThat(applicationContext.getBean(KnowledgeBaseMapper.class)).isNotNull();
    assertThat(applicationContext.getBean(ModelConfigController.class)).isNotNull();
    assertThat(applicationContext.getBean(ModelConfigMapper.class)).isNotNull();
  }
}
