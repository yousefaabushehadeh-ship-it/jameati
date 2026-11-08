package com.jameati.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(20)
public class AccessSensitiveCacheControlFilter extends OncePerRequestFilter {
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) return true;
    var path = request.getRequestURI();
    return !(path.startsWith("/api/v1/public/opportunities")
        || path.startsWith("/api/v1/public/catalog")
        || path.equals("/api/v1/public/membership-plan"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    response.setHeader("Cache-Control", "private, no-store, max-age=0");
    response.setHeader("Vary", "Cookie, Authorization");
    filterChain.doFilter(request, response);
  }
}
