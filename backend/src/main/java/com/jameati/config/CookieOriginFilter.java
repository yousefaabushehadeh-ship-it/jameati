package com.jameati.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.auth.AuthController;
import com.jameati.common.ApiResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CookieOriginFilter extends OncePerRequestFilter {
  private final ObjectMapper objectMapper;
  private final Set<String> allowedOrigins;

  public CookieOriginFilter(ObjectMapper objectMapper, @Value("${app.cors-origins}") String origins) {
    this.objectMapper = objectMapper;
    this.allowedOrigins = new HashSet<>(Arrays.stream(origins.split(","))
        .map(String::trim).filter(value -> !value.isBlank()).toList());
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) return true;
    var path = request.getRequestURI();
    return !path.startsWith("/api/v1/admin/")
        && !path.startsWith("/api/v1/me/")
        && !path.equals("/api/v1/auth/refresh")
        && !path.equals("/api/v1/auth/logout");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (usesCookieAuthentication(request) && !originAllowed(request)) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      objectMapper.writeValue(response.getOutputStream(), ApiResponse.error("مصدر الطلب غير مسموح"));
      return;
    }
    chain.doFilter(request, response);
  }

  private boolean originAllowed(HttpServletRequest request) {
    var origin = request.getHeader(HttpHeaders.ORIGIN);
    if (origin != null) return allowedOrigins.contains(origin);
    if ("cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"))) return false;
    var referer = request.getHeader(HttpHeaders.REFERER);
    if (referer == null || referer.isBlank()) return true;
    try {
      var uri = URI.create(referer);
      var refererOrigin = uri.getScheme() + "://" + uri.getAuthority();
      return allowedOrigins.contains(refererOrigin);
    } catch (Exception exception) {
      return false;
    }
  }

  private static boolean usesCookieAuthentication(HttpServletRequest request) {
    var authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization != null && authorization.startsWith("Bearer ")) return false;
    var cookies = request.getCookies();
    if (cookies == null) return false;
    for (var cookie : cookies) {
      if (AuthController.ACCESS_COOKIE.equals(cookie.getName())
          || AuthController.REFRESH_COOKIE.equals(cookie.getName())) return true;
    }
    return false;
  }
}
