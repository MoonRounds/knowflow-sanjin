package knowflow.sanjin.common.security;

import com.openai.core.RequestOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpRequestBody;
import com.openai.core.http.HttpResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;

/** OpenAI SDK transport that pins validated DNS answers and never follows redirects. */
public final class SecureOkHttpClient implements HttpClient {

  private final BaseUrlValidator baseUrlValidator;
  private final URI expectedBaseUri;
  private final OkHttpClient delegate;

  public SecureOkHttpClient(
      BaseUrlValidator baseUrlValidator,
      String baseUrl,
      Duration connectTimeout,
      Duration readTimeout,
      Duration requestTimeout) {
    this.baseUrlValidator = baseUrlValidator;
    this.expectedBaseUri = URI.create(baseUrl);
    baseUrlValidator.validate(baseUrl);
    this.delegate =
        new OkHttpClient.Builder()
            .connectTimeout(connectTimeout)
            .readTimeout(readTimeout)
            .callTimeout(requestTimeout)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .proxy(Proxy.NO_PROXY)
            .dns(this::resolvePinnedAddresses)
            .build();
  }

  @Override
  public HttpResponse execute(HttpRequest request, RequestOptions options) {
    try {
      return new OpenAiResponse(delegate.newCall(toOkHttpRequest(request)).execute());
    } catch (IOException e) {
      throw new IllegalStateException("Secure model HTTP request failed", e);
    }
  }

  @Override
  public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions options) {
    Call call = delegate.newCall(toOkHttpRequest(request));
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    call.enqueue(
        new Callback() {
          @Override
          public void onFailure(Call ignored, IOException error) {
            future.completeExceptionally(
                new IllegalStateException("Secure model HTTP request failed", error));
          }

          @Override
          public void onResponse(Call ignored, Response response) {
            future.complete(new OpenAiResponse(response));
          }
        });
    future.whenComplete(
        (ignored, error) -> {
          if (future.isCancelled()) {
            call.cancel();
          }
        });
    return future;
  }

  @Override
  public void close() {
    delegate.dispatcher().executorService().shutdown();
    delegate.connectionPool().evictAll();
  }

  private okhttp3.Request toOkHttpRequest(HttpRequest request) {
    URI uri = URI.create(request.url());
    baseUrlValidator.validate(uri.toString());
    assertExpectedAuthority(uri);

    okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(request.url());
    for (String name : request.headers().names()) {
      if (isRestrictedHeader(name)) {
        continue;
      }
      for (String value : request.headers().values(name)) {
        builder.addHeader(name, value);
      }
    }

    HttpRequestBody sourceBody = request.body();
    byte[] bytes = bodyBytes(sourceBody);
    MediaType contentType =
        sourceBody != null && sourceBody.contentType() != null
            ? MediaType.parse(sourceBody.contentType())
            : null;
    RequestBody body =
        bytes.length > 0 || permitsRequestBody(request.method().name())
            ? RequestBody.create(bytes, contentType)
            : null;
    builder.method(request.method().name(), body);
    return builder.build();
  }

  private List<InetAddress> resolvePinnedAddresses(String hostname) throws UnknownHostException {
    if (!hostname.equalsIgnoreCase(expectedBaseUri.getHost())) {
      throw new UnknownHostException("Unexpected model request host");
    }
    try {
      return baseUrlValidator.resolveForConnection(expectedBaseUri);
    } catch (IllegalArgumentException e) {
      UnknownHostException error =
          new UnknownHostException("Model host is not a safe public target");
      error.initCause(e);
      throw error;
    }
  }

  private void assertExpectedAuthority(URI uri) {
    if (!uri.getScheme().equalsIgnoreCase(expectedBaseUri.getScheme())
        || !uri.getHost().equalsIgnoreCase(expectedBaseUri.getHost())
        || effectivePort(uri) != effectivePort(expectedBaseUri)) {
      throw new IllegalArgumentException(
          "Model request authority differs from configured Base URL");
    }
  }

  private static int effectivePort(URI uri) {
    if (uri.getPort() >= 0) {
      return uri.getPort();
    }
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }

  private static boolean permitsRequestBody(String method) {
    return !"GET".equals(method) && !"HEAD".equals(method);
  }

  private static byte[] bodyBytes(HttpRequestBody body) {
    if (body == null) {
      return new byte[0];
    }
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      body.writeTo(output);
      return output.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Could not serialize model request body", e);
    }
  }

  private static boolean isRestrictedHeader(String name) {
    return "content-length".equalsIgnoreCase(name)
        || "host".equalsIgnoreCase(name)
        || "content-type".equalsIgnoreCase(name);
  }

  private static final class OpenAiResponse implements HttpResponse {

    private final Response delegate;

    private OpenAiResponse(Response delegate) {
      this.delegate = delegate;
    }

    @Override
    public int statusCode() {
      return delegate.code();
    }

    @Override
    public Headers headers() {
      return Headers.builder().putAll(delegate.headers().toMultimap()).build();
    }

    @Override
    public InputStream body() {
      return delegate.body() != null ? delegate.body().byteStream() : InputStream.nullInputStream();
    }

    @Override
    public void close() {
      delegate.close();
    }
  }
}
