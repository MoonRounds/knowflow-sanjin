package knowflow.sanjin.modules.conversation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import knowflow.sanjin.modules.conversation.service.GenerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** stop 端点是幂等操作，必须返回 204 空响应；若返回 200 空 body，前端统一 fetch 封装会对空体做 JSON 解析而抛错。 */
class GenerationControllerTest {

  private final GenerationService service = mock(GenerationService.class);
  private final GenerationController controller = new GenerationController(service);

  @Test
  @DisplayName("停止生成返回 204 No Content，前端无需解析响应体")
  void stopReturnsNoContent() {
    ResponseEntity<Void> response = controller.stop("1");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getBody()).isNull();
    verify(service).stop(1L);
  }
}
