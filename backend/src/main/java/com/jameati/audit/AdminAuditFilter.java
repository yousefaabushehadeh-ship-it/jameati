package com.jameati.audit;

import static com.jameati.audit.AuditDtos.*;

import com.jameati.config.CorrelationIdFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AdminAuditFilter extends OncePerRequestFilter {
  private final AuditLogService service;

  public AdminAuditFilter(AuditLogService service) {
    this.service = service;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v1/admin/")
        || switch (request.getMethod()) {
          case "POST", "PUT", "PATCH", "DELETE" -> false;
          default -> true;
        };
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Throwable failure = null;
    try {
      chain.doFilter(request, response);
    } catch (ServletException | IOException | RuntimeException exception) {
      failure = exception;
      throw exception;
    } finally {
      record(request, response, failure);
    }
  }

  private void record(HttpServletRequest request, HttpServletResponse response, Throwable failure) {
    try {
      var authentication = SecurityContextHolder.getContext().getAuthentication();
      var actor = authentication == null ? null : authentication.getName();
      var roles = authentication == null ? null : authentication.getAuthorities().stream()
          .map(Object::toString).sorted().collect(Collectors.joining(","));
      var path = request.getRequestURI().substring("/api/v1/admin/".length()).split("/");
      var resource = path.length == 0 || path[0].isBlank() ? "admin" : path[0];
      var resourceId = path.length > 1 ? path[path.length - 1] : null;
      var successful = failure == null && response.getStatus() < 400;
      var detail = failure == null ? "HTTP " + response.getStatus()
          : failure.getClass().getSimpleName() + ": " + failure.getMessage();
      service.record(new AuditCommand(actor, roles, request.getMethod(), resource, resourceId, detail,
          Objects.toString(request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE), null),
          request.getRemoteAddr(), successful));
    } catch (Exception ignored) {
      // Audit persistence must never change the outcome of the administration operation.
    }
  }
}
