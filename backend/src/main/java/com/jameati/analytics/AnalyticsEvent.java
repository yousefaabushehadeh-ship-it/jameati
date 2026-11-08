package com.jameati.analytics;

import com.jameati.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "analytics_events", indexes = {
    @Index(name = "idx_analytics_event_type_time", columnList = "event_type,occurred_at"),
    @Index(name = "idx_analytics_event_path_time", columnList = "path,occurred_at")})
@Getter @Setter @NoArgsConstructor
public class AnalyticsEvent extends BaseEntity {
  @Column(nullable = false, length = 60) private String eventType;
  @Column(length = 500) private String path;
  @Column(length = 50) private String entityType;
  @Column(length = 100) private String entityId;
  @Column(nullable = false, length = 64) private String visitorHash;
  @Column(columnDefinition = "text") private String metadataJson;
  @Column(nullable = false) private Instant occurredAt;
}
