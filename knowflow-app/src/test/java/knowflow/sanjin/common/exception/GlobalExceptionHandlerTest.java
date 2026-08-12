package knowflow.sanjin.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import knowflow.sanjin.common.error.ErrorCode;
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
}
