package knowflow.sanjin.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import knowflow.sanjin.common.exception.PreconditionRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiValueParserTest {

  @Test
  @DisplayName("should parse API IDs as positive integer strings")
  void shouldParsePositiveIds() {
    assertThat(ApiValueParser.positiveId("9223372036854775807", "id")).isEqualTo(Long.MAX_VALUE);
    assertThatThrownBy(() -> ApiValueParser.positiveId("0", "id"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ApiValueParser.positiveId("1.5", "id"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should require a strong quoted ETag")
  void shouldParseStrongEtag() {
    assertThat(ApiValueParser.requiredStrongEtagVersion("\"42\"")).isEqualTo(42);
    assertThat(ApiValueParser.strongEtag(42)).isEqualTo("\"42\"");
    assertThatThrownBy(() -> ApiValueParser.requiredStrongEtagVersion(null))
        .isInstanceOf(PreconditionRequiredException.class);
    assertThatThrownBy(() -> ApiValueParser.requiredStrongEtagVersion("W/\"42\""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ApiValueParser.requiredStrongEtagVersion("42"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
