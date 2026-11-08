package com.jameati.audit;

import java.time.Instant;

public final class AuditDtos {
  private AuditDtos() {}

  public record AuditResponse(
      Long id,
      String actorEmail,
      String actorRoles,
      String action,
      String resourceType,
      String resourceId,
      String detail,
      String correlationId,
      String ipAddress,
      boolean success,
      Instant occurredAt) {
    static AuditResponse of(AuditLog log) {
      return new AuditResponse(log.getId(), log.getActorEmail(), log.getActorRoles(), log.getAction(),
          log.getResourceType(), log.getResourceId(), log.getDetail(), log.getCorrelationId(),
          log.getIpAddress(), log.isSuccess(), log.getOccurredAt());
    }
  }

  public record AuditCommand(
      String actorEmail,
      String actorRoles,
      String action,
      String resourceType,
      String resourceId,
      String detail,
      String correlationId,
      String ipAddress,
      boolean success) {}
}
