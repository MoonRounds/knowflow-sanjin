package knowflow.sanjin.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个请求生成 correlationId 并放入 MDC，使同一请求的全部日志自动携带该标识。
 *
 * <p>响应头同时返回 {@code X-Correlation-Id}，供前端在报错时对照后端日志定位问题。请求结束后清理 MDC，避免线程池复用导致串号。
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String CORRELATION_ID_KEY = "correlationId";
  public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String correlationId = UUID.randomUUID().toString();
    MDC.put(CORRELATION_ID_KEY, correlationId);
    response.setHeader(CORRELATION_ID_HEADER, correlationId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(CORRELATION_ID_KEY);
    }
  }
}
