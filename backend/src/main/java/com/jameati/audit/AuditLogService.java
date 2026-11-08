package com.jameati.audit;

import static com.jameati.audit.AuditDtos.*;

import java.time.Instant;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true)
public class AuditLogService {
  private final AuditLogRepository repository;

  public AuditLogService(AuditLogRepository repository) {
    this.repository = repository;
  }

  public Page<AuditResponse> list(String actor, String resource, Pageable pageable) {
    return repository.search(normalize(actor), normalize(resource), pageable).map(AuditResponse::of);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AuditCommand command) {
    var log = new AuditLog();
    log.setActorEmail(limit(command.actorEmail(), 190));
    log.setActorRoles(limit(command.actorRoles(), 500));
    log.setAction(limit(command.action(), 80));
    log.setResourceType(limit(command.resourceType(), 80));
    log.setResourceId(limit(command.resourceId(), 190));
    log.setDetail(limit(command.detail(), 2_000));
    log.setCorrelationId(limit(command.correlationId(), 100));
    log.setIpAddress(limit(command.ipAddress(), 64));
    log.setSuccess(command.success());
    log.setOccurredAt(Instant.now());
    repository.save(log);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private static String limit(String value, int max) {
    if (value == null) return null;
    return value.substring(0, Math.min(max, value.length()));
  }
}
