package knowflow.sanjin.modules.conversation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.memory.MemoryService;
import knowflow.sanjin.modules.conversation.service.ConversationService;
import knowflow.sanjin.modules.conversation.service.GenerationTraceService;
import knowflow.sanjin.modules.conversation.vo.MessagePageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 消息历史 Controller 分页测试：游标只在确有更早记录时返回，首屏始终保留最新消息。 */
class ConversationControllerTest {

  private ConversationService service;
  private ConversationController controller;

  @BeforeEach
  void setUp() {
    service = mock(ConversationService.class);
    GenerationTraceService traceService = mock(GenerationTraceService.class);
    when(traceService.loadByAssistantMessageIds(anyList())).thenReturn(Map.of());
    controller = new ConversationController(service, mock(MemoryService.class), traceService);
  }

  @Test
  @DisplayName("新会话只有两条消息时不返回更早游标")
  void omitsCursorWhenNoEarlierMessagesExist() {
    when(service.listMessages(1L, null, 21)).thenReturn(messages(1, 2));

    MessagePageResponse response = controller.messages("1", null, 20);

    assertThat(response.getMessages()).hasSize(2);
    assertThat(response.getNextBefore()).isNull();
  }

  @Test
  @DisplayName("超过一页时返回最新二十条及准确的更早游标")
  void returnsLatestPageAndCursorWhenEarlierMessagesExist() {
    when(service.listMessages(1L, null, 21)).thenReturn(messages(1, 21));

    MessagePageResponse response = controller.messages("1", null, 20);

    assertThat(response.getMessages()).hasSize(20);
    assertThat(response.getMessages().get(0).getId()).isEqualTo("2");
    assertThat(response.getMessages().get(19).getId()).isEqualTo("21");
    assertThat(response.getNextBefore()).isEqualTo("2");
  }

  private static List<ChatMessage> messages(long first, long last) {
    List<ChatMessage> messages = new ArrayList<>();
    for (long sequence = first; sequence <= last; sequence++) {
      ChatMessage message = new ChatMessage();
      message.setId(sequence);
      message.setConversationId(1L);
      message.setOwnerId(1L);
      message.setSequence(sequence);
      message.setRole(ChatMessage.ROLE_USER);
      message.setContent("message-" + sequence);
      messages.add(message);
    }
    return messages;
  }
}
