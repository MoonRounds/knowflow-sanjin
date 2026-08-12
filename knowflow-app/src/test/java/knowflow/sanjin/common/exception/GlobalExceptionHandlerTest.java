package knowflow.sanjin.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.modelconfig.exception.UtilityModelNotConfiguredException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;

class GlobalExceptionHandlerTest {

  @Test
  void unsupportedMediaTypeKeepsItsHttpSemantics() {
    ResponseEntity<ProblemDetail> response =
        new GlobalExceptionHandler()
            .handleUnsupportedMediaType(mock(HttpMediaTypeNotSupportedException.class));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getProperties())
        .containsEntry("errorCode", ErrorCode.UNSUPPORTED_MEDIA_TYPE)
        .containsKey("correlationId");
  }

  @Test
  void utilityModelNotConfiguredReturnsBadRequestWithStableCode() {
    ResponseEntity<ProblemDetail> response =
        new GlobalExceptionHandler()
            .handleUtilityModelNotConfigured(new UtilityModelNotConfiguredException());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getProperties())
        .containsEntry("errorCode", ErrorCode.UTILITY_MODEL_NOT_CONFIGURED);
    assertThat(response.getBody().getDetail()).contains("Utility");
  }
}
