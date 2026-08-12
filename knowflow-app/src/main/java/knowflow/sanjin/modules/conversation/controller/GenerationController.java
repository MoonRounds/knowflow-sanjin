package knowflow.sanjin.modules.conversation.controller;

import jakarta.validation.Valid;
import knowflow.sanjin.common.util.ApiValueParser;
import knowflow.sanjin.modules.conversation.dto.RegenerateRequest;
import knowflow.sanjin.modules.conversation.dto.SendMessageRequest;
import knowflow.sanjin.modules.conversation.service.GenerationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("${knowflow.api.base-path:/api/v1}")
public class GenerationController {

  private final GenerationService generationService;

  public GenerationController(GenerationService generationService) {
    this.generationService = generationService;
  }

  /** 发送消息并流式生成回答（SSE）。body 携带 clientMessageId 幂等 + 可选 modelConfigId。 */
  @PostMapping(value = "/conversations/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter send(@PathVariable String id, @Valid @RequestBody SendMessageRequest request) {
    return generationService.send(ApiValueParser.positiveId(id, "id"), request);
  }

  /** 重新生成：在最新 assistant 消息上创建新 attempt（SSE）。 */
  @PostMapping(
      value = "/conversations/{id}/messages/regenerate",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter regenerate(
      @PathVariable String id, @RequestBody(required = false) RegenerateRequest request) {
    return generationService.regenerate(ApiValueParser.positiveId(id, "id"), request);
  }

  /** 停止当前 active generation（释放 slot，写取消状态）。 */
  @PostMapping("/conversations/{id}/stop")
  public void stop(@PathVariable String id) {
    generationService.stop(ApiValueParser.positiveId(id, "id"));
  }
}
