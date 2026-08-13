package knowflow.sanjin.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * V12 单归属重构迁移测试：不启动 Spring，直接驱动 Flyway，验证「干净库」与「存量库」双路径。
 *
 * <p>存量库路径：先 migrate 到 V11 → 插入 V1 存量形态数据（10 Item / 多 KB 关联 / candidate CSV 多值）→ migrate
 * 到最新 → 断言 kb_id 回填、status→deleted 映射、candidate 单值、关联表删除等（对应 v1.5-phase-01-plan.md G6/G7/G8/G11）。
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V12 knowledge_document 迁移（干净库 + 存量库）")
class MigrationV12IT {

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
          .withDatabaseName("knowflow_test")
          .withUsername("test")
          .withPassword("test");

  private static final String DB = "knowflow_test";

  @BeforeEach
  void resetSchema() throws SQLException {
    try (Connection c = connection(); Statement s = c.createStatement()) {
      s.execute("DROP DATABASE IF EXISTS " + DB);
      s.execute("CREATE DATABASE " + DB + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }
  }

  private Connection connection() throws SQLException {
    return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
  }

  private void migrateTo(String target) {
    Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .target(MigrationVersion.fromVersion(target))
        .load()
        .migrate();
  }

  private void migrateToLatest() {
    Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .load()
        .migrate();
  }

  @Test
  @DisplayName("干净库：V1–V12 全量迁移成功，新表结构就绪")
  void cleanDatabaseMigrates() throws Exception {
    migrateToLatest();
    try (Connection c = connection(); Statement s = c.createStatement()) {
      assertThat(tableExists(s, "knowledge_document")).isTrue();
      assertThat(tableExists(s, "knowledge_document_chunk")).isTrue();
      assertThat(tableExists(s, "knowledge_document_tag")).isTrue();
      assertThat(tableExists(s, "knowledge_item")).isFalse();
      assertThat(tableExists(s, "knowledge_chunk")).isFalse();
      assertThat(tableExists(s, "knowledge_item_tag")).isFalse();
      assertThat(tableExists(s, "knowledge_base_item")).isFalse();
      assertThat(columnExists(s, "knowledge_document", "kb_id")).isTrue();
      assertThat(columnExists(s, "knowledge_document", "deleted")).isTrue();
      assertThat(columnExists(s, "knowledge_document", "status")).isFalse();
      assertThat(columnExists(s, "knowledge_document_chunk", "knowledge_document_id")).isTrue();
      assertThat(columnExists(s, "knowledge_document_tag", "knowledge_document_id")).isTrue();
      assertThat(columnExists(s, "file_metadata", "knowledge_document_id")).isTrue();
      assertThat(columnExists(s, "knowledge_candidate", "ai_knowledge_base_id")).isTrue();
      assertThat(columnExists(s, "knowledge_candidate", "draft_knowledge_base_id")).isTrue();
      assertThat(columnExists(s, "knowledge_candidate", "ai_knowledge_base_ids")).isFalse();
      assertThat(constraintExists(s, "tag", "uk_tag_owner_active_normalized_name")).isTrue();
    }
  }

  @Test
  @DisplayName("存量库：V11 数据迁移到 V12，kb_id 回填/软删映射/candidate 单值正确")
  void legacyDatabaseMigratesWithBackfill() throws Exception {
    migrateTo("11");
    seedLegacyData();
    migrateToLatest();

    try (Connection c = connection(); Statement s = c.createStatement()) {
      // 所有文档都拿到 kb_id（无孤儿）
      assertThat(scalarLong(s, "SELECT COUNT(*) FROM knowledge_document WHERE kb_id IS NULL"))
          .isZero();

      // 多关联取最小 KB id（101 → 10，而不是 20）
      assertThat(scalarLong(s, "SELECT kb_id FROM knowledge_document WHERE id = 101")).isEqualTo(10L);
      // 仅有关联已软删的兜底（102 → 20）
      assertThat(scalarLong(s, "SELECT kb_id FROM knowledge_document WHERE id = 102")).isEqualTo(20L);

      // status → deleted 映射：ACTIVE→0，DELETED→1
      assertThat(scalarLong(s, "SELECT deleted FROM knowledge_document WHERE id = 100")).isZero();
      assertThat(scalarLong(s, "SELECT deleted FROM knowledge_document WHERE id = 108")).isEqualTo(1L);

      // chunk / tag 关联表的列已改名并保留数据
      assertThat(scalarLong(s, "SELECT knowledge_document_id FROM knowledge_document_chunk WHERE id = 1000"))
          .isEqualTo(100L);
      assertThat(scalarLong(s, "SELECT knowledge_document_id FROM knowledge_document_tag WHERE id = 1"))
          .isEqualTo(100L);
      // file_metadata 列已改名
      assertThat(scalarLong(s, "SELECT knowledge_document_id FROM file_metadata WHERE id = 1"))
          .isEqualTo(105L);

      // candidate CSV → 单值：取首元素；空串 → NULL
      assertThat(scalarString(s, "SELECT ai_knowledge_base_id FROM knowledge_candidate WHERE id = 1"))
          .isEqualTo("10");
      assertThat(scalarString(s, "SELECT draft_knowledge_base_id FROM knowledge_candidate WHERE id = 1"))
          .isEqualTo("10");
      assertThat(scalarString(s, "SELECT ai_knowledge_base_id FROM knowledge_candidate WHERE id = 2"))
          .isNull();
      assertThat(scalarString(s, "SELECT draft_knowledge_base_id FROM knowledge_candidate WHERE id = 2"))
          .isEqualTo("20");

      // tag 唯一约束：软删行与活动行同名可共存，两个活动同名行被拒绝
      exec(s, "INSERT INTO tag (id, owner_id, name, normalized_name, deleted) VALUES (3, 1, '笔记二', '笔记', 1)");
      assertThatThrownBy(
              () ->
                  exec(
                      s,
                      "INSERT INTO tag (id, owner_id, name, normalized_name, deleted) VALUES (4, 1, '笔记三', '笔记', 0)"))
          .isInstanceOf(SQLException.class);
    }
  }

  private void seedLegacyData() throws SQLException {
    try (Connection c = connection(); Statement s = c.createStatement()) {
      // 知识库（2 个，测试多关联首值）
      exec(s, "INSERT INTO knowledge_base (id, owner_id, display_name, normalized_name) VALUES (10, 1, 'KB-A', 'kb-a')");
      exec(s, "INSERT INTO knowledge_base (id, owner_id, display_name, normalized_name) VALUES (20, 1, 'KB-B', 'kb-b')");

      // 10 个知识条目：8 ACTIVE + 2 DELETED
      for (int i = 0; i < 10; i++) {
        long id = 100L + i;
        String status = i >= 8 ? "DELETED" : "ACTIVE";
        exec(
            s,
            "INSERT INTO knowledge_item (id, owner_id, source_type, title, summary, content, content_version, "
                + "indexed_version, index_status, status) VALUES ("
                + id
                + ", 1, 'MANUAL_NOTE', 'note"
                + id
                + "', NULL, 'body"
                + id
                + "', 1, 1, 'INDEXED', '"
                + status
                + "')");
      }

      // 关联表：多对多（含一条多关联、一条仅软删关联）
      exec(s, "INSERT INTO knowledge_base_item (knowledge_base_id, knowledge_item_id, owner_id, deleted) VALUES (10, 100, 1, 0)");
      exec(s, "INSERT INTO knowledge_base_item (knowledge_base_id, knowledge_item_id, owner_id, deleted) VALUES (10, 101, 1, 0)");
      exec(s, "INSERT INTO knowledge_base_item (knowledge_base_id, knowledge_item_id, owner_id, deleted) VALUES (20, 101, 1, 0)");
      exec(s, "INSERT INTO knowledge_base_item (knowledge_base_id, knowledge_item_id, owner_id, deleted) VALUES (20, 102, 1, 1)");
      for (int i = 3; i < 10; i++) {
        long id = 100L + i;
        exec(s, "INSERT INTO knowledge_base_item (knowledge_base_id, knowledge_item_id, owner_id, deleted) VALUES (10, " + id + ", 1, 0)");
      }

      // chunk
      exec(
          s,
          "INSERT INTO knowledge_chunk (id, knowledge_item_id, owner_id, content_version, chunk_index, chunk_id, content) "
              + "VALUES (1000, 100, 1, 1, 0, 'chunk-100-1-0', 'chunk body 0')");
      exec(
          s,
          "INSERT INTO knowledge_chunk (id, knowledge_item_id, owner_id, content_version, chunk_index, chunk_id, content) "
              + "VALUES (1001, 100, 1, 1, 1, 'chunk-100-1-1', 'chunk body 1')");

      // tag + 关联
      exec(s, "INSERT INTO tag (id, owner_id, name, normalized_name) VALUES (1, 1, '笔记', '笔记')");
      exec(s, "INSERT INTO tag (id, owner_id, name, normalized_name) VALUES (2, 1, 'AI', 'ai')");
      exec(s, "INSERT INTO knowledge_item_tag (id, knowledge_item_id, tag_id, owner_id, deleted) VALUES (1, 100, 1, 1, 0)");

      // 候选：CSV 多值 / 空串
      exec(
          s,
          "INSERT INTO conversation (id, owner_id, title) VALUES (1, 1, '新会话')");
      exec(
          s,
          "INSERT INTO chat_message (id, conversation_id, owner_id, role, sequence, content) VALUES (1, 1, 1, 'USER', 1, 'hi')");
      exec(
          s,
          "INSERT INTO processing_task (id, owner_id, task_type, business_key, business_id) "
              + "VALUES (1, 1, 'EXTRACTION', 'ek:1:1', 1)");
      exec(
          s,
          "INSERT INTO knowledge_extraction_task (id, owner_id, conversation_id, cutoff_message_id, extraction_profile, "
              + "profile_version, utility_revision_id, processing_task_id, input_char_count) "
              + "VALUES (1, 1, 1, 1, 'default', 1, 1, 1, 10)");
      exec(
          s,
          "INSERT INTO knowledge_candidate (id, owner_id, extraction_task_id, status, ai_title, ai_content, "
              + "ai_knowledge_base_ids, ai_tags, draft_title, draft_content, draft_knowledge_base_ids, draft_tags) "
              + "VALUES (1, 1, 1, 'PENDING', 't1', 'c1', '10,20', 'x', 't1', 'c1', '10', 'x')");
      exec(
          s,
          "INSERT INTO knowledge_candidate (id, owner_id, extraction_task_id, status, ai_title, ai_content, "
              + "ai_knowledge_base_ids, ai_tags, draft_title, draft_content, draft_knowledge_base_ids, draft_tags) "
              + "VALUES (2, 1, 1, 'PENDING', 't2', 'c2', '', '', 't2', 'c2', ' 20 ', '')");

      // file_metadata（一对一）
      exec(
          s,
          "INSERT INTO file_metadata (id, owner_id, knowledge_item_id, storage_key, original_filename, content_type, "
              + "detected_mime_type, byte_size, sha256) "
              + "VALUES (1, 1, 105, 's1', 'a.md', 'text/markdown', 'text/markdown', 10, REPEAT('a', 64))");
    }
  }

  private boolean tableExists(Statement s, String table) throws SQLException {
    try (ResultSet rs =
        s.executeQuery(
            "SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = '"
                + DB
                + "' AND TABLE_NAME = '"
                + table
                + "'")) {
      return rs.next();
    }
  }

  private boolean columnExists(Statement s, String table, String column) throws SQLException {
    try (ResultSet rs =
        s.executeQuery(
            "SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '"
                + DB
                + "' AND TABLE_NAME = '"
                + table
                + "' AND COLUMN_NAME = '"
                + column
                + "'")) {
      return rs.next();
    }
  }

  private boolean constraintExists(Statement s, String table, String constraint) throws SQLException {
    try (ResultSet rs =
        s.executeQuery(
            "SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = '"
                + DB
                + "' AND TABLE_NAME = '"
                + table
                + "' AND CONSTRAINT_NAME = '"
                + constraint
                + "'")) {
      return rs.next();
    }
  }

  private long scalarLong(Statement s, String sql) throws SQLException {
    try (ResultSet rs = s.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private String scalarString(Statement s, String sql) throws SQLException {
    try (ResultSet rs = s.executeQuery(sql)) {
      rs.next();
      return rs.getString(1);
    }
  }

  private void exec(Statement s, String sql) throws SQLException {
    s.executeUpdate(sql);
  }
}
