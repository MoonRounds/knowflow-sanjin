package knowflow.sanjin.modules.conversation.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.testinfra.RedisMemoryTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/** ChatMemoryRepositoryImpl 的 Redis 集成测试：真实序列化/TTL/key 隔离/删除。 */
@SpringBootTest
class ChatMemoryRepositoryRedisIT extends RedisMemoryTestBase {

  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private ChatMemoryRepositoryImpl repository;

  @Test
  void persistsAndLoadsWithRealRedisSerialization() {
    repository.saveAll("100", List.of(new UserMessage("你好"), new AssistantMessage("世界")));

    List<Message> loaded = repository.findByConversationId("100");
    assertThat(loaded).hasSize(2);
    assertThat(loaded.get(0)).isInstanceOf(UserMessage.class);
    assertThat(loaded.get(0).getText()).isEqualTo("你好");
    assertThat(loaded.get(1)).isInstanceOf(AssistantMessage.class);
    assertThat(loaded.get(1).getText()).isEqualTo("世界");
  }

  @Test
  void setsTtlOnSaveAndRefreshOnOverwrite() {
    repository.saveAll("101", List.of(new UserMessage("u")));
    Long ttl1 = redisTemplate.getExpire("knowflow:chat-memory:v1:1:chat:101");
    assertThat(ttl1).isNotNull().isGreaterThan(0);

    // 覆盖写入刷新 TTL
    repository.saveAll("101", List.of(new UserMessage("u2"), new AssistantMessage("a2")));
    Long ttl2 = redisTemplate.getExpire("knowflow:chat-memory:v1:1:chat:101");
    assertThat(ttl2).isNotNull().isGreaterThan(0);
  }

  @Test
  void isolatesKeysAcrossConversationsInRedis() {
    repository.saveAll("102", List.of(new UserMessage("u102")));
    repository.saveAll("103", List.of(new UserMessage("u103")));

    Set<String> keys = redisTemplate.keys("knowflow:chat-memory:v1:1:chat:*");
    assertThat(keys).isNotNull();
    assertThat(keys)
        .containsExactlyInAnyOrder(
            "knowflow:chat-memory:v1:1:chat:102", "knowflow:chat-memory:v1:1:chat:103");
  }

  @Test
  void deleteRemovesKeyFromRedis() {
    repository.saveAll("104", List.of(new UserMessage("u")));
    repository.deleteByConversationId("104");

    assertThat(redisTemplate.hasKey("knowflow:chat-memory:v1:1:chat:104")).isFalse();
    assertThat(repository.findByConversationId("104")).isEmpty();
  }

  @Test
  void expiredKeyBecomesMiss() throws Exception {
    MemoryProperties shortTtl = new MemoryProperties();
    shortTtl.setTtl(Duration.ofSeconds(1));
    ChatMemoryRepositoryImpl repo =
        new ChatMemoryRepositoryImpl(
            new RedisMemoryStore(redisTemplate, new tools.jackson.databind.ObjectMapper()),
            shortTtl,
            new CurrentOwnerProvider());
    repo.saveAll("105", List.of(new UserMessage("u")));

    // 等 TTL 过期
    Thread.sleep(1500);
    assertThat(repo.findByConversationId("105")).isEmpty();
  }
}
