package com.jameati.crm;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrmFollowUpRepository
    extends JpaRepository<CrmFollowUp, Long>, JpaSpecificationExecutor<CrmFollowUp> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select followUp from CrmFollowUp followUp where followUp.id=:id and followUp.deleted=false")
  Optional<CrmFollowUp> findByIdForUpdate(@Param("id") Long id);

  List<CrmFollowUp> findByStudentLeadIdAndStatusAndDeletedFalseOrderByDueAtAsc(
      Long leadId, FollowUpStatus status);

  List<CrmFollowUp> findByB2bAccountIdAndStatusAndDeletedFalseOrderByDueAtAsc(
      Long accountId, FollowUpStatus status);

  long countByDeletedFalseAndStatusAndDueAtBefore(
      FollowUpStatus status, Instant before);

  @Query("select count(followUp) from CrmFollowUp followUp "
      + "where followUp.deleted=false and followUp.status=:status "
      + "and followUp.dueAt>=:from and followUp.dueAt<:to")
  long countPendingBetween(
      @Param("status") FollowUpStatus status,
      @Param("from") Instant from,
      @Param("to") Instant to);
}
