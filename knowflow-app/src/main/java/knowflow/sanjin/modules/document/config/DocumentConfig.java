package knowflow.sanjin.modules.document.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 文档上传配置装配：注册 {@code knowflow.document.*}。 */
@Configuration
@EnableConfigurationProperties(DocumentProperties.class)
public class DocumentConfig {}
