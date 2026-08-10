package knowflow.sanjin.common.config;

import knowflow.sanjin.common.security.BaseUrlValidator;
import knowflow.sanjin.modules.knowledge.infrastructure.EmbeddingClient;
import knowflow.sanjin.modules.knowledge.infrastructure.QdrantClient;
import knowflow.sanjin.modules.knowledge.service.TextChunker;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 索引基础设施装配：Chunk 策略、Embedding 客户端、Qdrant 客户端。 */
@Configuration
@EnableConfigurationProperties({
  ChunkingProperties.class,
  EmbeddingProperties.class,
  QdrantProperties.class
})
public class IndexInfrastructureConfig {

  @Bean
  public TextChunker textChunker(ChunkingProperties properties) {
    return new TextChunker(properties);
  }

  @Bean
  public EmbeddingClient embeddingClient(
      EmbeddingProperties properties, BaseUrlValidator baseUrlValidator) {
    return new EmbeddingClient(properties, baseUrlValidator);
  }

  @Bean
  public QdrantClient qdrantClient(QdrantProperties properties) {
    return new QdrantClient(properties);
  }
}
