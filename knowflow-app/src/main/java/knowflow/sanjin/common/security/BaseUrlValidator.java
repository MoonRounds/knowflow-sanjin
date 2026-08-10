package knowflow.sanjin.common.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Base URL 安全校验：只允许安全 HTTPS 云端目标，阻止 localhost、回环、私网、 内嵌凭据和基于 DNS 解析的私网地址。
 *
 * <p>V1 单用户可信内网环境，此校验在保存与实际请求前都会执行；模型 HTTP 传输层同时禁止自动重定向。
 */
public final class BaseUrlValidator {

  private final boolean allowLocalDev;
  private final HostResolver hostResolver;

  public BaseUrlValidator(boolean allowLocalDev) {
    this(allowLocalDev, InetAddress::getAllByName);
  }

  BaseUrlValidator(boolean allowLocalDev, HostResolver hostResolver) {
    this.allowLocalDev = allowLocalDev;
    this.hostResolver = hostResolver;
  }

  public void validate(String baseUrl) {
    if (!StringUtils.hasText(baseUrl)) {
      throw new IllegalArgumentException("Base URL must not be empty");
    }
    String trimmed = baseUrl.trim();
    URI uri;
    try {
      uri = URI.create(trimmed);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Base URL is not a valid URI");
    }
    validateStructure(uri);
    if (isLocalAllowed(uri)) {
      resolve(uri.getHost());
      return;
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      throw new IllegalArgumentException("Base URL must use HTTPS");
    }
    resolvePublicAddresses(uri.getHost());
  }

  /** Resolves and returns the exact validated addresses that the HTTP transport must use. */
  List<InetAddress> resolveForConnection(URI uri) {
    validateStructure(uri);
    if (isLocalAllowed(uri)) {
      return resolve(uri.getHost());
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      throw new IllegalArgumentException("Base URL must use HTTPS");
    }
    return resolvePublicAddresses(uri.getHost());
  }

  private void validateStructure(URI uri) {
    if (uri.getScheme() == null || uri.getHost() == null) {
      throw new IllegalArgumentException("Base URL must be an absolute URL with a host");
    }
    if (uri.getUserInfo() != null) {
      throw new IllegalArgumentException("Base URL must not contain embedded credentials");
    }
    if (uri.getFragment() != null) {
      throw new IllegalArgumentException("Base URL must not contain a fragment");
    }
  }

  private boolean isLocalAllowed(URI uri) {
    if (!allowLocalDev) {
      return false;
    }
    if (!"http".equalsIgnoreCase(uri.getScheme())) {
      return false;
    }
    String host = uri.getHost();
    return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
  }

  private List<InetAddress> resolvePublicAddresses(String host) {
    if (isLoopbackName(host)) {
      throw new IllegalArgumentException("Base URL must not point to localhost or loopback");
    }
    List<InetAddress> addresses = resolve(host);
    for (InetAddress address : addresses) {
      if (isUnsafeAddress(address)) {
        throw new IllegalArgumentException(
            "Base URL must not point to loopback, private, link-local or multicast network");
      }
    }
    return addresses;
  }

  private List<InetAddress> resolve(String host) {
    try {
      InetAddress[] addresses = hostResolver.resolve(host);
      if (addresses == null || addresses.length == 0) {
        throw new IllegalArgumentException("Base URL host must resolve to a public address");
      }
      return Arrays.asList(addresses);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Base URL host must resolve to a public address");
    }
  }

  private boolean isLoopbackName(String host) {
    String h = host.toLowerCase();
    return h.equals("localhost") || h.equals("127.0.0.1") || h.equals("::1");
  }

  private boolean isUnsafeAddress(InetAddress address) {
    byte[] bytes = address.getAddress();
    boolean carrierGradeNat =
        bytes.length == 4 && unsigned(bytes[0]) == 100 && (unsigned(bytes[1]) & 0xc0) == 64;
    boolean ipv6UniqueLocal = bytes.length == 16 && (unsigned(bytes[0]) & 0xfe) == 0xfc;
    return address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isSiteLocalAddress()
        || address.isLinkLocalAddress()
        || address.isMulticastAddress()
        || carrierGradeNat
        || ipv6UniqueLocal;
  }

  private static int unsigned(byte value) {
    return value & 0xff;
  }

  @FunctionalInterface
  interface HostResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }
}
