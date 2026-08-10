package knowflow.sanjin.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** 规范 Chunk：MySQL 是正文事实源，Qdrant 只保存确定性 Point ID 与 metadata，不存完整正文。 */
@TableName("knowledge_chunk")
public class KnowledgeChunk {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long knowledgeItemId;

  private Long ownerId;

  private Integer contentVersion;

  private Integer chunkIndex;

  private String chunkId;

  private String content;

  private String headingPath;

  @TableField(fill = FieldFill.INSERT)
  private Instant createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getKnowledgeItemId() {
    return knowledgeItemId;
  }

  public void setKnowledgeItemId(Long knowledgeItemId) {
    this.knowledgeItemId = knowledgeItemId;
  }

  public Long getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(Long ownerId) {
    this.ownerId = ownerId;
  }

  public Integer getContentVersion() {
    return contentVersion;
  }

  public void setContentVersion(Integer contentVersion) {
    this.contentVersion = contentVersion;
  }

  public Integer getChunkIndex() {
    return chunkIndex;
  }

  public void setChunkIndex(Integer chunkIndex) {
    this.chunkIndex = chunkIndex;
  }

  public String getChunkId() {
    return chunkId;
  }

  public void setChunkId(String chunkId) {
    this.chunkId = chunkId;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getHeadingPath() {
    return headingPath;
  }

  public void setHeadingPath(String headingPath) {
    this.headingPath = headingPath;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
