package knowflow.sanjin.modules.conversation.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import knowflow.sanjin.common.util.ApiValueParser;
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
  public ConversationResponse get(@PathVariable String id) {
    return ConversationAssembler.toResponse(
        service.getByIdAndOwner(ApiValueParser.positiveId(id, "id")));
  }

  @PatchMapping("/conversations/{id}")
  public ConversationResponse update(
      @PathVariable String id, @Valid @RequestBody UpdateConversationRequest request) {
    return ConversationAssembler.toResponse(
        service.update(ApiValueParser.positiveId(id, "id"), request));
  }

  @DeleteMapping("/conversations/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    long conversationId = ApiValueParser.positiveId(id, "id");
    service.softDelete(conversationId);
    // 删除事务提交后清理 Memory 投影（容错：Redis 故障不阻塞删除）
    memoryService.clear(conversationId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/conversations/{id}/messages")
  public MessagePageResponse messages(
      @PathVariable String id,
      @RequestParam(name = "before", required = false) String before,
      @RequestParam(name = "limit", defaultValue = "20") int limit) {
    Long conversationId = ApiValueParser.positiveId(id, "id");
    Long beforeSeq = before == null ? null : ApiValueParser.positiveId(before, "before");
    int pageSize = Math.max(1, Math.min(limit, 100));
    // 多取一条只用于判断是否确有更早消息，避免新会话也错误显示“加载更早的消息”。
    List<ChatMessage> page = service.listMessages(conversationId, beforeSeq, pageSize + 1);
    boolean hasEarlier = page.size() > pageSize;
    if (hasEarlier) {
      // service 返回正序；额外取到的是当前页之前最老的那一条。
      page = new java.util.ArrayList<>(page.subList(1, page.size()));
    }
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
    if (hasEarlier && !messages.isEmpty()) {
      // 下一页为比当前页第一条（最小 sequence）更早的消息
      response.setNextBefore(page.get(0).getSequence().toString());
    }
    return response;
  }
}
