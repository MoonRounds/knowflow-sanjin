package knowflow.sanjin.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import java.time.Instant;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisPlusConfig {

  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    return interceptor;
  }

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
