package com.jameati.audit;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
  @Query("select a from AuditLog a where a.deleted=false and (:actor='' or lower(a.actorEmail) like lower(concat('%',:actor,'%'))) and (:resource='' or lower(a.resourceType)=lower(:resource))")
  Page<AuditLog> search(String actor, String resource, Pageable pageable);
}
