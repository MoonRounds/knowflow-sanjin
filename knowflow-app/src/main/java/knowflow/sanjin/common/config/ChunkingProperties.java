package knowflow.sanjin.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 文本 Chunk 策略配置：块大小、overlap 与上限。 */
@ConfigurationProperties(prefix = "knowflow.chunking")
public class ChunkingProperties {

  private int targetChars = 800;

  private int overlapChars = 200;

  private int maxChunks = 64;

  public int getTargetChars() {
    return targetChars;
  }

  public void setTargetChars(int targetChars) {
    this.targetChars = targetChars;
  }

  public int getOverlapChars() {
    return overlapChars;
  }

  public void setOverlapChars(int overlapChars) {
    this.overlapChars = overlapChars;
  }

  public int getMaxChunks() {
    return maxChunks;
  }

  public void setMaxChunks(int maxChunks) {
    this.maxChunks = maxChunks;
  }
}
