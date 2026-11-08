package com.jameati.analytics;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class AnalyticsDtos {
  private AnalyticsDtos() {}

  public record EventRequest(
      @NotBlank @Size(max = 60)
          @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_.-]*$", message = "نوع الحدث غير صالح")
          String eventType,
      @Size(max = 500) String path,
      @Size(max = 50) String entityType,
      @Size(max = 100) String entityId,
      @Size(max = 500) String referrer,
      @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "معرف الجلسة غير صالح") String sessionId,
      @Size(max = 25) Map<@Size(max = 80) String, Object> metadata) {}

  public record CountItem(String name, long count) {}

  public record Summary(
      int days,
      Instant from,
      Instant to,
      long totalEvents,
      long uniqueVisitors,
      List<CountItem> events,
      List<CountItem> topPaths) {}
}
