package com.jameati.analytics;

import static com.jameati.analytics.AnalyticsDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {
  private final AnalyticsEventRepository repository;
  private final ObjectMapper objectMapper;
  private final String hashSecret;

  public AnalyticsService(
      AnalyticsEventRepository repository,
      ObjectMapper objectMapper,
      @Value("${app.analytics.hash-secret:local-analytics-hash-secret-change-in-production}") String hashSecret) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.hashSecret = hashSecret;
  }

  @Transactional
  public void ingest(EventRequest request, HttpServletRequest servletRequest) {
    var event = new AnalyticsEvent();
    event.setEventType(request.eventType().trim().toLowerCase(Locale.ROOT));
    event.setPath(normalizePath(request.path()));
    event.setEntityType(trimToNull(request.entityType()));
    event.setEntityId(trimToNull(request.entityId()));
    event.setMetadataJson(metadataJson(request));
    event.setVisitorHash(visitorHash(request.sessionId(), servletRequest));
    event.setOccurredAt(Instant.now());
    repository.save(event);
  }

  public Summary summary(int days) {
    if (days < 1 || days > 365) throw new IllegalArgumentException("days يجب أن يكون بين 1 و365");
    var to = Instant.now();
    var from = to.minus(days, ChronoUnit.DAYS);
    var events = repository.eventCounts(from, to).stream()
        .map(value -> new CountItem(value.getName(), value.getTotal())).toList();
    var paths = repository.topPaths(from, to, PageRequest.of(0, 10)).stream()
        .map(value -> new CountItem(value.getName(), value.getTotal())).toList();
    return new Summary(days, from, to,
        repository.countByOccurredAtBetweenAndDeletedFalse(from, to),
        repository.uniqueVisitors(from, to), events, paths);
  }

  private String metadataJson(EventRequest request) {
    var metadata = new LinkedHashMap<String, Object>();
    if (request.metadata() != null) metadata.putAll(request.metadata());
    if (request.referrer() != null && !request.referrer().isBlank()) {
      metadata.put("referrer", stripQuery(request.referrer().trim()));
    }
    if (metadata.isEmpty()) return null;
    try {
      var json = objectMapper.writeValueAsString(metadata);
      if (json.length() > 8_000) throw new IllegalArgumentException("بيانات الحدث أكبر من المسموح");
      return json;
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("بيانات الحدث غير صالحة");
    }
  }

  private String visitorHash(String sessionId, HttpServletRequest request) {
    var identifier = sessionId == null || sessionId.isBlank()
        ? request.getRemoteAddr() + "|" + Objects.toString(request.getHeader("User-Agent"), "")
        : sessionId;
    var value = LocalDate.now(ZoneOffset.UTC) + "|" + identifier + "|" + hashSecret;
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static String normalizePath(String value) {
    var path = trimToNull(value);
    if (path == null) return null;
    int separator = path.indexOf('?');
    if (separator >= 0) path = path.substring(0, separator);
    separator = path.indexOf('#');
    if (separator >= 0) path = path.substring(0, separator);
    if (!path.startsWith("/") || path.startsWith("//")) throw new IllegalArgumentException("مسار الحدث غير صالح");
    return path;
  }

  private static String stripQuery(String value) {
    int separator = value.indexOf('?');
    if (separator >= 0) value = value.substring(0, separator);
    separator = value.indexOf('#');
    return separator >= 0 ? value.substring(0, separator) : value;
  }

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
