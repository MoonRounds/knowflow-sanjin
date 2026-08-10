package knowflow.sanjin.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import knowflow.sanjin.testinfra.MySQLTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** OpenAPI 契约集成测试：运行时生成的 API 文档必须与签入的快照一致，防止前后端契约漂移。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("OpenAPI Contract Integration Test")
class OpenApiContractIT extends MySQLTestBase {

  private static final Path SNAPSHOT = locateSnapshot();

  @LocalServerPort private int port;

  @Test
  @DisplayName("runtime OpenAPI should match the checked-in normalized snapshot")
  void shouldMatchSnapshot() throws Exception {
    HttpResponse<String> health = get("/api/v1/health");
    assertThat(health.statusCode()).isEqualTo(200);
    assertThat(health.body()).contains("UP");

    HttpResponse<String> response = get("/v3/api-docs");
    assertThat(response.statusCode()).isEqualTo(200);

    String actual = normalize(response.body());
    String outputPath = System.getProperty("knowflow.openapi.output");
    if (outputPath != null && !outputPath.isBlank()) {
      Files.writeString(Path.of(outputPath), actual, StandardCharsets.UTF_8);
      return;
    }

    assertThat(Files.exists(SNAPSHOT)).as("checked-in OpenAPI snapshot").isTrue();
    assertThat(actual).isEqualTo(normalize(Files.readString(SNAPSHOT)));
  }

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static String normalize(String json) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    JsonNode root = mapper.readTree(json);
    if (root.isObject()) {
      ((com.fasterxml.jackson.databind.node.ObjectNode) root).remove("servers");
    }
    Object value = mapper.treeToValue(root, Object.class);
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
        + System.lineSeparator();
  }

  private static Path locateSnapshot() {
    Path fromRepositoryRoot = Path.of("docs/api/openapi.json").toAbsolutePath().normalize();
    if (Files.exists(fromRepositoryRoot)) {
      return fromRepositoryRoot;
    }
    return Path.of("../docs/api/openapi.json").toAbsolutePath().normalize();
  }
}
