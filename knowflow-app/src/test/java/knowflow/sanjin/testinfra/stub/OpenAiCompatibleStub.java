package knowflow.sanjin.testinfra.stub;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 本地 OpenAI-Compatible Stub，用于契约与能力测试，不调用真实云端。
 *
 * <p>覆盖普通响应、流式（SSE）、usage、401、429、timeout 和非法 JSON。 通过 {@link #setBehavior} 切换响应。Base URL 形如 {@code
 * http://127.0.0.1:{port}/v1}（端点路径 {@code /v1/chat/completions}）。
 */
public final class OpenAiCompatibleStub implements AutoCloseable {

  public enum Behavior {
    NORMAL,
    UNAUTHORIZED, // 401
    RATE_LIMITED, // 429
    MALFORMED_JSON, // 200 但非法 JSON
    TIMEOUT, // 挂起直至客户端超时
    REDIRECT, // 302 必须被客户端拒绝，不能自动跟随
    STREAM // SSE 流式响应
  }

  private final HttpServer server;
  private final AtomicReference<Behavior> behavior = new AtomicReference<>(Behavior.NORMAL);

  private OpenAiCompatibleStub(HttpServer server) {
    this.server = server;
  }

  public static OpenAiCompatibleStub start() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    OpenAiCompatibleStub stub = new OpenAiCompatibleStub(server);
    server.createContext("/v1/chat/completions", stub.new ChatCompletionsHandler());
    server.createContext("/v1/embeddings", stub.new EmbeddingsHandler());
    server.start();
    return stub;
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
  }

  public void setBehavior(Behavior behavior) {
    this.behavior.set(behavior);
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private final class EmbeddingsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      if (!"POST".equals(exchange.getRequestMethod())) {
        respond(exchange, 405, "{\"error\":\"method not allowed\"}");
        return;
      }
      switch (behavior.get()) {
        case UNAUTHORIZED:
          respond(
              exchange,
              401,
              "{\"error\":{\"message\":\"Invalid API key\",\"code\":\"invalid_api_key\"}}");
          return;
        case RATE_LIMITED:
          respond(
              exchange,
              429,
              "{\"error\":{\"message\":\"Rate limit exceeded\",\"code\":\"rate_limit_exceeded\"}}");
          return;
        case MALFORMED_JSON:
          respond(exchange, 200, "{ not json");
          return;
        case TIMEOUT:
          exchange.close();
          return;
        case NORMAL:
        default:
          respondNormal(exchange);
      }
    }

    private void respondNormal(HttpExchange exchange) throws IOException {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      int inputCount = 0;
      if (body != null && body.contains("\"input\":[")) {
        // 粗粒度统计输入条数（数组元素个数）
        int start = body.indexOf('[');
        int end = body.lastIndexOf(']');
        if (start >= 0 && end > start) {
          String array = body.substring(start + 1, end);
          if (array.isBlank()) {
            inputCount = 0;
          } else {
            inputCount = array.split(",\"").length;
          }
        }
      } else {
        inputCount = 1;
      }
      // 固定 4 维向量便于断言；真实维度由 EmbeddingClient 校验
      StringBuilder sb = new StringBuilder("{\"object\":\"list\",\"data\":[");
      for (int i = 0; i < inputCount; i++) {
        if (i > 0) {
          sb.append(',');
        }
        sb.append("{\"object\":\"embedding\",\"index\":")
            .append(i)
            .append(",\"embedding\":[0.1,0.2,0.3,0.4]}");
      }
      sb.append("],\"model\":\"stub-embedding\"}");
      respond(exchange, 200, sb.toString());
    }
  }

  private final class ChatCompletionsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      if (!"POST".equals(exchange.getRequestMethod())) {
        respond(exchange, 405, "{\"error\":\"method not allowed\"}");
        return;
      }
      switch (behavior.get()) {
        case UNAUTHORIZED:
          respond(
              exchange,
              401,
              "{\"error\":{\"message\":\"Invalid API key\",\"code\":\"invalid_api_key\"}}");
          return;
        case RATE_LIMITED:
          respond(
              exchange,
              429,
              "{\"error\":{\"message\":\"Rate limit exceeded\",\"code\":\"rate_limit_exceeded\"}}");
          return;
        case TIMEOUT:
          // 模拟对端无响应/挂起：立即关闭连接。避免 Thread.sleep 阻塞 JDK HttpServer
          // handler 线程导致 server.stop(0) 等待，保证契约测试快速且确定。
          exchange.close();
          return;
        case MALFORMED_JSON:
          respond(exchange, 200, "{ this is not valid json");
          return;
        case REDIRECT:
          exchange.getResponseHeaders().set("Location", baseUrl() + "/chat/completions");
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
          return;
        case STREAM:
          respondStream(exchange);
          return;
        case NORMAL:
        default:
          respondNormal(exchange);
      }
    }

    private void respondNormal(HttpExchange exchange) throws IOException {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      if (body != null && body.contains("\"stream\":true")) {
        respondStream(exchange);
        return;
      }
      if (body.contains("retrievalQuery") && body.contains("knowledgeBases")) {
        respond(
            exchange,
            200,
            completion("{\"needRag\":false,\"knowledgeBases\":[],\"retrievalQuery\":\"test\"}"));
        return;
      }
      if (body.contains("knowledgeBaseId") && body.contains("candidates")) {
        respond(exchange, 200, completion("{\"candidates\":[]}"));
        return;
      }
      respond(exchange, 200, completion("pong"));
    }

    private String completion(String content) {
      String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
      return "{\"id\":\"chatcmpl-stub\",\"object\":\"chat.completion\",\"created\":1700000000,"
          + "\"model\":\"stub-model\",\"choices\":[{\"index\":0,"
          + "\"message\":{\"role\":\"assistant\",\"content\":\""
          + escaped
          + "\"},"
          + "\"finish_reason\":\"stop\"}],"
          + "\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":12,\"total_tokens\":21}}";
    }

    private void respondStream(HttpExchange exchange) throws IOException {
      String chunks =
          "data: {\"id\":\"chatcmpl-stub\",\"object\":\"chat.completion.chunk\",\"model\":\"stub-model\","
              + "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Hel\"},\"finish_reason\":null}]}\n\n"
              + "data: {\"id\":\"chatcmpl-stub\",\"object\":\"chat.completion.chunk\",\"model\":\"stub-model\","
              + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"lo\"},\"finish_reason\":null}]}\n\n"
              + "data: {\"id\":\"chatcmpl-stub\",\"object\":\"chat.completion.chunk\",\"model\":\"stub-model\","
              + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
              + "data: [DONE]\n\n";
      exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, 0);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(chunks.getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }
}
