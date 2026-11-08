package com.jameati.audit;

import com.jameati.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_actor_time", columnList = "actor_email,occurred_at"),
    @Index(name = "idx_audit_resource_time", columnList = "resource_type,occurred_at")})
@Getter @Setter @NoArgsConstructor
public class AuditLog extends BaseEntity {
  @Column(length = 190) private String actorEmail;
  @Column(length = 500) private String actorRoles;
  @Column(nullable = false, length = 80) private String action;
  @Column(nullable = false, length = 80) private String resourceType;
  @Column(length = 190) private String resourceId;
  @Column(columnDefinition = "text") private String detail;
  @Column(length = 100) private String correlationId;
  @Column(length = 64) private String ipAddress;
  @Column(nullable = false) private boolean success;
  @Column(nullable = false) private Instant occurredAt;
}
