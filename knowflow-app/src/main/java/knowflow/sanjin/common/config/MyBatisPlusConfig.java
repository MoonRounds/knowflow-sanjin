package knowflow.sanjin.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import java.time.Instant;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MyBatis-Plus 通用配置：乐观锁插件与审计时间自动填充。 */
@Configuration
public class MyBatisPlusConfig {

  /** 乐观锁插件：仅对 updateById(entity) 生效（Conversation 使用）；wrapper 手写乐观锁的实体不依赖此插件。 */
  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    return interceptor;
  }

  /** 审计时间填充：createdAt/updatedAt 按字段策略自动写入当前时间。 */
  @Bean
  public MetaObjectHandler metaObjectHandler() {
    return new MetaObjectHandler() {
      @Override
      public void insertFill(MetaObject metaObject) {
        Instant now = Instant.now();
        this.strictInsertFill(metaObject, "createdAt", Instant.class, now);
        this.strictInsertFill(metaObject, "updatedAt", Instant.class, now);
      }

      @Override
      public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", Instant.class, Instant.now());
      }
    };
  }
}
