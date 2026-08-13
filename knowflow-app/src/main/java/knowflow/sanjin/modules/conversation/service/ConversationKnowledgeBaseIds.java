package knowflow.sanjin.modules.conversation.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import knowflow.sanjin.common.util.ApiValueParser;

/** Conversation 知识库 JSON 的规范化边界：数据库 NULL=自动，API 空数组=自动。 */
public final class ConversationKnowledgeBaseIds {

  public static final int MAX_IDS = 50;

  private ConversationKnowledgeBaseIds() {}

  public static List<Long> normalizeApiIds(List<String> rawIds) {
    if (rawIds == null) {
      throw new IllegalArgumentException("knowledgeBaseIds 不能为 null");
    }
    LinkedHashSet<Long> unique = new LinkedHashSet<>();
    for (String raw : rawIds) {
      unique.add(ApiValueParser.positiveId(raw, "knowledgeBaseIds"));
    }
    if (unique.size() > MAX_IDS) {
      throw new IllegalArgumentException("knowledgeBaseIds 最多允许 " + MAX_IDS + " 个");
    }
    return unique.stream().sorted(Comparator.naturalOrder()).toList();
  }

  public static String encode(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return null;
    }
    return "[" + String.join(",", ids.stream().map(String::valueOf).toList()) + "]";
  }

  public static List<Long> decode(String json) {
    if (json == null || json.isBlank() || "[]".equals(json.trim())) {
      return List.of();
    }
    String value = json.trim();
    if (!value.startsWith("[") || !value.endsWith("]")) {
      throw new IllegalStateException("conversation.knowledge_base_ids JSON 格式非法");
    }
    String body = value.substring(1, value.length() - 1).trim();
    if (body.isEmpty()) {
      return List.of();
    }
    List<Long> result = new ArrayList<>();
    for (String part : body.split(",")) {
      try {
        long id = Long.parseLong(part.trim());
        if (id <= 0) {
          throw new NumberFormatException("non-positive");
        }
        result.add(id);
      } catch (NumberFormatException e) {
        throw new IllegalStateException("conversation.knowledge_base_ids JSON 格式非法", e);
      }
    }
    return result.stream().distinct().sorted().toList();
  }

  public static List<String> decodeAsStrings(String json) {
    return decode(json).stream().map(String::valueOf).toList();
  }
}
