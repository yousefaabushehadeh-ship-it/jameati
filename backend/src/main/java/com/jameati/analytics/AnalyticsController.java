package com.jameati.analytics;

import static com.jameati.analytics.AnalyticsDtos.*;

import com.jameati.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class AnalyticsController {
  private final AnalyticsService service;

  public AnalyticsController(AnalyticsService service) {
    this.service = service;
  }

  @PostMapping("/api/v1/public/analytics/events")
  ResponseEntity<Void> ingest(@Valid @RequestBody EventRequest request, HttpServletRequest servletRequest) {
    service.ingest(request, servletRequest);
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/api/v1/admin/analytics/summary")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ApiResponse<Summary> summary(@RequestParam(defaultValue = "30") int days) {
    return ApiResponse.ok(service.summary(days));
  }
}
