package knowflow.sanjin.common.config;

import static org.assertj.core.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecretSecurityConfigTest {

  @Test
  @DisplayName("should fail fast when master key is missing")
  void shouldFailWhenMasterKeyMissing() {
    SecretKeyProperties props = new SecretKeyProperties();
    SecretSecurityConfig config = new SecretSecurityConfig();
    assertThatThrownBy(() -> config.secretEncryptionService(props))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("KNOWFLOW_SECURITY_MASTER_KEY");
  }

  @Test
  @DisplayName("should reject non-Base64 master key")
  void shouldRejectInvalidBase64() {
    SecretKeyProperties props = new SecretKeyProperties();
    props.setMasterKey("!!!not-base64!!!");
    SecretSecurityConfig config = new SecretSecurityConfig();
    assertThatThrownBy(() -> config.secretEncryptionService(props))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Base64");
  }

  @Test
  @DisplayName("should require an exact AES-256 key length")
  void shouldRequireExactly32Bytes() {
    SecretSecurityConfig config = new SecretSecurityConfig();
    for (int length : new int[] {16, 24, 31, 33}) {
      SecretKeyProperties props = new SecretKeyProperties();
      props.setMasterKey(Base64.getEncoder().encodeToString(new byte[length]));
      assertThatThrownBy(() -> config.secretEncryptionService(props))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("32 bytes");
    }
  }

  @Test
  @DisplayName("should require a positive encryption version")
  void shouldRequirePositiveVersion() {
    SecretKeyProperties props = new SecretKeyProperties();
    props.setMasterKey(Base64.getEncoder().encodeToString(new byte[32]));
    props.setEncryptionVersion(0);

    assertThatThrownBy(() -> new SecretSecurityConfig().secretEncryptionService(props))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("positive");
  }
}
