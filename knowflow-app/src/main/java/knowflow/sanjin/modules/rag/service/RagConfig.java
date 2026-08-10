package knowflow.sanjin.modules.rag.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** RAG 配置装配：启用 {@link RagProperties}。 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {}
