package knowflow.sanjin.modules.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import knowflow.sanjin.common.config.QdrantProperties;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.knowledge.exception.RetryableIndexException;
import org.junit.jupiter.api.Test;

class QdrantClientTest {

  @Test
  void connectionFailureMapsToQdrantUnavailable() {
    QdrantProperties properties = new QdrantProperties();
    properties.setBaseUrl("http://127.0.0.1:1");
    properties.setConnectTimeoutMillis(100);
    properties.setReadTimeoutMillis(100);
    QdrantClient client = new QdrantClient(properties);

    assertThatThrownBy(() -> client.ensureCollection("failure-drill", 4))
        .isInstanceOfSatisfying(
            RetryableIndexException.class,
            error -> assertThat(error.getFailureCode()).isEqualTo(ErrorCode.QDRANT_UNAVAILABLE));
  }
}
