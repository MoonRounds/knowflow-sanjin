package knowflow.sanjin.modules.extraction.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 提取配置装配：注册 {@code knowflow.extraction.*}。 */
@Configuration
@EnableConfigurationProperties(ExtractionProperties.class)
public class ExtractionConfig {}
