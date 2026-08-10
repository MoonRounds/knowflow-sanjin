package knowflow.sanjin.modules.conversation.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import knowflow.sanjin.modules.conversation.assembler.ConversationAssembler;
import knowflow.sanjin.modules.conversation.assembler.MessageAssembler;
import knowflow.sanjin.modules.conversation.dto.CreateConversationRequest;
import knowflow.sanjin.modules.conversation.dto.UpdateConversationRequest;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.entity.Conversation;
import knowflow.sanjin.modules.conversation.entity.GenerationTrace;
import knowflow.sanjin.modules.conversation.memory.MemoryService;
import knowflow.sanjin.modules.conversation.service.ConversationService;
import knowflow.sanjin.modules.conversation.service.GenerationTraceService;
import knowflow.sanjin.modules.conversation.vo.ConversationResponse;
import knowflow.sanjin.modules.conversation.vo.MessagePageResponse;
import knowflow.sanjin.modules.conversation.vo.MessageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${knowflow.api.base-path:/api/v1}")
public class ConversationController {

  private final ConversationService service;
  private final MemoryService memoryService;
  private final GenerationTraceService traceService;

  public ConversationController(
      ConversationService service,
      MemoryService memoryService,
      GenerationTraceService traceService) {
    this.service = service;
    this.memoryService = memoryService;
    this.traceService = traceService;
  }

  @PostMapping("/conversations")
  public ResponseEntity<ConversationResponse> create(
      @Valid @RequestBody CreateConversationRequest request) {
    Conversation c = service.create(request);
    ConversationResponse response = ConversationAssembler.toResponse(c);
    return ResponseEntity.created(URI.create("/api/v1/conversations/" + response.getId()))
        .body(response);
  }

  @GetMapping("/conversations")
  public List<ConversationResponse> list() {
    return ConversationAssembler.toResponseList(service.listForOwner());
  }

  @GetMapping("/conversations/{id}")
  public ConversationResponse get(@PathVariable Long id) {
    return ConversationAssembler.toResponse(service.getByIdAndOwner(id));
  }

  @PatchMapping("/conversations/{id}")
  public ConversationResponse update(
      @PathVariable Long id, @Valid @RequestBody UpdateConversationRequest request) {
    return ConversationAssembler.toResponse(service.update(id, request));
  }

  @DeleteMapping("/conversations/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.softDelete(id);
    // 删除事务提交后清理 Memory 投影（容错：Redis 故障不阻塞删除）
    memoryService.clear(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/conversations/{id}/messages")
  public MessagePageResponse messages(
      @PathVariable Long id,
      @RequestParam(name = "before", required = false) Long before,
      @RequestParam(name = "limit", defaultValue = "20") int limit) {
    List<ChatMessage> page = service.listMessages(id, before, limit);
    MessagePageResponse response = new MessagePageResponse();
    // 预载 trace 快照，assistant 消息内嵌当时 sources/cited
    List<Long> assistantIds =
        page.stream()
            .filter(m -> ChatMessage.ROLE_ASSISTANT.equals(m.getRole()))
            .map(ChatMessage::getId)
            .toList();
    Map<Long, GenerationTrace> traces = traceService.loadByAssistantMessageIds(assistantIds);
    List<MessageResponse> messages = MessageAssembler.toResponseList(page, traces, traceService);
    response.setMessages(messages);
    if (!messages.isEmpty()) {
      // 下一页为比当前页第一条（最小 sequence）更早的消息
      response.setNextBefore(page.get(0).getId().toString());
    }
    return response;
  }
}
