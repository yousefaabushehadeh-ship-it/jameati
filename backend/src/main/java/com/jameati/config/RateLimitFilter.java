package com.jameati.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.common.ApiResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {
  private final ObjectMapper objectMapper;
  private final int authLimit;
  private final int contactLimit;
  private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
  private final AtomicLong requests = new AtomicLong();

  public RateLimitFilter(
      ObjectMapper objectMapper,
      @Value("${app.rate-limit.auth-requests-per-minute}") int authLimit,
      @Value("${app.rate-limit.contact-requests-per-minute}") int contactLimit) {
    this.objectMapper = objectMapper;
    this.authLimit = positive(authLimit);
    this.contactLimit = positive(contactLimit);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!"POST".equals(request.getMethod())) return true;
    var path = request.getRequestURI();
    return !path.startsWith("/api/v1/auth/") && !path.equals("/api/v1/public/support/contact");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var path = request.getRequestURI();
    var auth = path.startsWith("/api/v1/auth/");
    var bucket = auth ? "auth" : "contact";
    var limit = auth ? authLimit : contactLimit;
    var now = System.currentTimeMillis();
    var window = windows.compute(request.getRemoteAddr() + "|" + bucket,
        (key, current) -> current == null || now >= current.resetAt
            ? new Window(now + 60_000, 1) : current.incremented());
    if (requests.incrementAndGet() % 500 == 0) windows.entrySet().removeIf(entry -> now >= entry.getValue().resetAt);
    if (window.count > limit) {
      response.setStatus(429);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      response.setHeader("Retry-After", Long.toString(Math.max(1, (window.resetAt - now + 999) / 1000)));
      objectMapper.writeValue(response.getOutputStream(), ApiResponse.error("طلبات كثيرة؛ حاول مرة أخرى لاحقًا"));
      return;
    }
    chain.doFilter(request, response);
  }

  private static int positive(int value) {
    if (value < 1) throw new IllegalArgumentException("حد الطلبات يجب أن يكون موجبًا");
    return value;
  }

  private record Window(long resetAt, int count) {
    Window incremented() { return new Window(resetAt, count + 1); }
  }
}
