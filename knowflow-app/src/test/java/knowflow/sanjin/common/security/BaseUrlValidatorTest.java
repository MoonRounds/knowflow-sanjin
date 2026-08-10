package knowflow.sanjin.common.security;

import static org.assertj.core.api.Assertions.*;

import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** BaseUrlValidator 单元测试：SSRF 防护规则（拒绝私网/回环/localhost/内嵌凭据，仅 HTTPS）。 */
class BaseUrlValidatorTest {

  private static final BaseUrlValidator STRICT = new BaseUrlValidator(false);

  @Test
  @DisplayName("should accept a public HTTPS address without relying on DNS")
  void shouldAcceptHttps() {
    STRICT.validate("https://1.1.1.1/v1");
  }

  @Test
  @DisplayName("should reject empty or malformed base url")
  void shouldRejectMalformed() {
    assertThatThrownBy(() -> STRICT.validate("")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> STRICT.validate("   ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> STRICT.validate("not-a-url"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> STRICT.validate("ftp://api.example.com"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should reject http non-local in strict mode")
  void shouldRejectHttp() {
    assertThatThrownBy(() -> STRICT.validate("http://api.example.com/v1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("HTTPS");
  }

  @Test
  @DisplayName("should reject localhost and loopback")
  void shouldRejectLocalhost() {
    assertThatThrownBy(() -> STRICT.validate("https://localhost:8080/v1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> STRICT.validate("https://127.0.0.1/v1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> STRICT.validate("http://localhost:8080/v1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should reject embedded credentials")
  void shouldRejectCredentials() {
    assertThatThrownBy(() -> STRICT.validate("https://user:pass@api.example.com/v1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("credential");
  }

  @Test
  @DisplayName("should reject private and link-local addresses by DNS resolution")
  void shouldRejectPrivateAddresses() {
    assertThatThrownBy(() -> STRICT.validate("https://192.168.1.10/v1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> STRICT.validate("https://10.0.0.5/v1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> STRICT.validate("https://172.16.0.1/v1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> STRICT.validate("https://169.254.169.254/v1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should fail closed when the hostname cannot be resolved")
  void shouldRejectUnresolvedHost() {
    assertThatThrownBy(() -> STRICT.validate("https://unresolvable.invalid/v1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resolve");
  }

  @Test
  @DisplayName("should fail closed when DNS returns no addresses")
  void shouldRejectEmptyDnsAnswer() {
    BaseUrlValidator validator = new BaseUrlValidator(false, ignored -> new InetAddress[0]);

    assertThatThrownBy(() -> validator.validate("https://provider.example/v1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resolve");
  }

  @Test
  @DisplayName("should allow local http base url when local dev enabled")
  void shouldAllowLocalDev() {
    BaseUrlValidator permissive = new BaseUrlValidator(true);
    assertThatCode(() -> permissive.validate("http://localhost:8080/v1"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName(
      "should reject a private DNS answer at connection time after a public save-time answer")
  void shouldRejectDnsRebinding() throws Exception {
    AtomicInteger lookup = new AtomicInteger();
    BaseUrlValidator validator =
        new BaseUrlValidator(
            false,
            host ->
                lookup.getAndIncrement() == 0
                    ? new InetAddress[] {InetAddress.getByAddress(host, new byte[] {1, 1, 1, 1})}
                    : new InetAddress[] {
                      InetAddress.getByAddress(host, new byte[] {127, 0, 0, 1})
                    });

    validator.validate("https://provider.example/v1");
    assertThatThrownBy(
            () -> validator.resolveForConnection(URI.create("https://provider.example/v1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("private");
  }
}
