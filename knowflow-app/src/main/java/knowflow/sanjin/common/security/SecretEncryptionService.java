package knowflow.sanjin.common.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 使用应用级主密钥的认证加密（AES-256-GCM + 随机 nonce）。
 *
 * <p>输出格式：{@code v{version}:{base64(nonce || ciphertext)}}。nonce 为 12 字节随机值， 与密文一同存储，无需保密。密文包含 GCM
 * 认证标签，任何篡改都会导致解密失败。
 */
public final class SecretEncryptionService {

  static final int NONCE_LENGTH = 12;
  static final int GCM_TAG_BITS = 128;
  static final String AES = "AES";
  static final String AES_GCM = "AES/GCM/NoPadding";
  static final String FORMAT_PREFIX = "v";

  private final SecretKey masterKey;
  private final int encryptionVersion;
  private final SecureRandom secureRandom = new SecureRandom();

  public SecretEncryptionService(byte[] masterKeyBytes, int encryptionVersion) {
    if (masterKeyBytes == null || masterKeyBytes.length != 32) {
      throw new IllegalArgumentException("master key must be exactly 32 bytes for AES-256");
    }
    if (encryptionVersion <= 0) {
      throw new IllegalArgumentException("encryption version must be a positive integer");
    }
    this.masterKey = new SecretKeySpec(masterKeyBytes, AES);
    this.encryptionVersion = encryptionVersion;
  }

  public int getEncryptionVersion() {
    return encryptionVersion;
  }

  /** 加密明文，返回 {@code v{version}:{base64(nonce || ciphertext)}}。 */
  public String encrypt(String plaintext) {
    if (plaintext == null) {
      return null;
    }
    try {
      byte[] nonce = new byte[NONCE_LENGTH];
      secureRandom.nextBytes(nonce);

      Cipher cipher = Cipher.getInstance(AES_GCM);
      cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      byte[] payload = new byte[nonce.length + ciphertext.length];
      System.arraycopy(nonce, 0, payload, 0, nonce.length);
      System.arraycopy(ciphertext, 0, payload, nonce.length, ciphertext.length);

      return FORMAT_PREFIX + encryptionVersion + ":" + Base64.getEncoder().encodeToString(payload);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to encrypt API key", e);
    }
  }

  /** 解密。密文版本必须与当前主密钥版本一致；格式错误或篡改（认证失败）抛异常。 */
  public String decrypt(String stored) {
    if (stored == null) {
      return null;
    }
    if (!stored.startsWith(FORMAT_PREFIX)) {
      throw new IllegalArgumentException("Unsupported ciphertext format");
    }
    int colon = stored.indexOf(':');
    if (colon < 0) {
      throw new IllegalArgumentException("Malformed ciphertext");
    }
    int version;
    try {
      version = Integer.parseInt(stored.substring(1, colon));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Malformed ciphertext", e);
    }
    if (version != encryptionVersion) {
      throw new IllegalArgumentException(
          "Ciphertext version "
              + version
              + " does not match master key version "
              + encryptionVersion);
    }
    try {
      byte[] payload = Base64.getDecoder().decode(stored.substring(colon + 1));
      if (payload.length < NONCE_LENGTH + GCM_TAG_BITS / 8) {
        throw new IllegalArgumentException("Ciphertext too short");
      }

      byte[] nonce = new byte[NONCE_LENGTH];
      System.arraycopy(payload, 0, nonce, 0, NONCE_LENGTH);
      byte[] ciphertext = new byte[payload.length - NONCE_LENGTH];
      System.arraycopy(payload, NONCE_LENGTH, ciphertext, 0, ciphertext.length);

      Cipher cipher = Cipher.getInstance(AES_GCM);
      cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
      byte[] plaintext = cipher.doFinal(ciphertext);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalArgumentException("Failed to decrypt API key", e);
    }
  }
}
