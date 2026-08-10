package knowflow.sanjin.common.security;

import static org.assertj.core.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecretEncryptionServiceTest {

  private static SecretEncryptionService service() {
    return new SecretEncryptionService(
        Base64.getDecoder().decode("S25vd0Zsb3ctVGVzdC1NYXN0ZXItS2V5LTAxMjM0NTY="), 1);
  }

  @Test
  @DisplayName("should round-trip encrypt and decrypt")
  void shouldRoundTrip() {
    SecretEncryptionService service = service();
    String secret = "sk-test-abcdefghijklmnopqrstuvwxyz";
    String stored = service.encrypt(secret);
    assertThat(service.decrypt(stored)).isEqualTo(secret);
  }

  @Test
  @DisplayName("should produce different ciphertext for same plaintext (random nonce)")
  void shouldUseRandomNonce() {
    SecretEncryptionService service = service();
    String secret = "sk-test-repeated-secret";
    String first = service.encrypt(secret);
    String second = service.encrypt(secret);
    assertThat(first).isNotEqualTo(second);
  }

  @Test
  @DisplayName("should fail to decrypt with a different master key")
  void shouldFailWithWrongKey() {
    SecretEncryptionService service = service();
    String stored = service.encrypt("sk-test-wrong-key");
    SecretEncryptionService other =
        new SecretEncryptionService(
            Base64.getDecoder().decode("S25vd0Zsb3ctVGVzdC1LZXkyLUZvci1Lbm93Rmxvdy0="), 1);
    assertThatThrownBy(() -> other.decrypt(stored))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("decrypt");
  }

  @Test
  @DisplayName("should fail on tampered ciphertext")
  void shouldFailOnTampering() {
    SecretEncryptionService service = service();
    String stored = service.encrypt("sk-test-tamper");
    // 翻转一个字符，破坏密文或认证标签
    StringBuilder sb = new StringBuilder(stored);
    int idx = stored.length() - 2;
    char orig = sb.charAt(idx);
    sb.setCharAt(idx, orig == 'A' ? 'B' : 'A');
    assertThatThrownBy(() -> service.decrypt(sb.toString()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should reject ciphertext with mismatched encryption version")
  void shouldRejectVersionMismatch() {
    SecretEncryptionService service =
        new SecretEncryptionService(
            Base64.getDecoder().decode("S25vd0Zsb3ctVGVzdC1NYXN0ZXItS2V5LTAxMjM0NTY="), 2);
    SecretEncryptionService v1 =
        new SecretEncryptionService(
            Base64.getDecoder().decode("S25vd0Zsb3ctVGVzdC1NYXN0ZXItS2V5LTAxMjM0NTY="), 1);
    String stored = v1.encrypt("sk-test-version");
    assertThatThrownBy(() -> service.decrypt(stored))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("version");
  }

  @Test
  @DisplayName("should reject malformed ciphertext")
  void shouldRejectMalformed() {
    SecretEncryptionService service = service();
    assertThatThrownBy(() -> service.decrypt("not-a-ciphertext"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.decrypt("v1:!!!notbase64!!!"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.decrypt("v2:short"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should reject empty master key")
  void shouldRejectEmptyMasterKey() {
    assertThatThrownBy(() -> new SecretEncryptionService(new byte[0], 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SecretEncryptionService(null, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should reject non-AES-256 keys and non-positive versions")
  void shouldRejectInvalidKeyConfiguration() {
    assertThatThrownBy(() -> new SecretEncryptionService(new byte[16], 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 bytes");
    assertThatThrownBy(() -> new SecretEncryptionService(new byte[32], 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("positive");
  }

  @Test
  @DisplayName("should mask short and long secrets")
  void shouldMask() {
    assertThat(SecretRedactor.mask("sk-abcdefghij")).isEqualTo("sk-a******ghij");
    assertThat(SecretRedactor.mask("short")).isEqualTo("********");
    assertThat(SecretRedactor.mask("")).isEmpty();
    assertThat(SecretRedactor.maskForDisplay("sk-abcdefghij")).isEqualTo("sk-a************");
  }

  @Test
  @DisplayName("should redact common API key patterns from logs")
  void shouldRedactLogText() {
    String text = "request apiKey=sk-abcdefghij123456 token here";
    String redacted = SecretRedactor.redact(text);
    assertThat(redacted).doesNotContain("sk-abcdefghij123456");
    assertThat(redacted).contains("****");
  }
}
