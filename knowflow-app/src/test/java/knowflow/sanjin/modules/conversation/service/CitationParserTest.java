package knowflow.sanjin.modules.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import knowflow.sanjin.modules.rag.dto.RetrievedSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CitationParser 单元测试：只标记本次检索集合内有效 {@code [Sx]}，忽略不存在编号，不伪造来源。 */
class CitationParserTest {

  private RetrievedSource source(String id) {
    RetrievedSource s = new RetrievedSource();
    s.setSourceId(id);
    s.setCited(false);
    return s;
  }

  @Test
  @DisplayName("should mark cited only for valid in-range references")
  void shouldMarkValidCitations() {
    List<RetrievedSource> sources = new ArrayList<>(List.of(source("a"), source("b"), source("c")));

    List<RetrievedSource> result = CitationParser.markCited(sources, "Answer [S2] and [S1].");

    assertThat(result.get(0).isCited()).isTrue();
    assertThat(result.get(1).isCited()).isTrue();
    assertThat(result.get(2).isCited()).isFalse();
  }

  @Test
  @DisplayName("should ignore out-of-range references like [S99]")
  void shouldIgnoreOutOfRange() {
    List<RetrievedSource> sources = new ArrayList<>(List.of(source("a")));

    List<RetrievedSource> result = CitationParser.markCited(sources, "Answer [S99].");

    assertThat(result.get(0).isCited()).isFalse();
  }

  @Test
  @DisplayName("should ignore [S0] and malformed tokens")
  void shouldIgnoreZeroAndMalformed() {
    List<RetrievedSource> sources = new ArrayList<>(List.of(source("a")));

    List<RetrievedSource> result = CitationParser.markCited(sources, "S0 [S0] [Sx] [S1]");

    assertThat(result.get(0).isCited()).isTrue();
  }

  @Test
  @DisplayName("should not mark cited when content is blank or sources empty")
  void shouldNotMarkWhenNoContent() {
    List<RetrievedSource> sources = new ArrayList<>(List.of(source("a")));

    assertThat(CitationParser.markCited(sources, ""))
        .extracting(RetrievedSource::isCited)
        .containsExactly(false);
    assertThat(CitationParser.markCited(List.of(), " [S1] ")).isEmpty();
  }
}
