package com.jameati.crm;

import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrmActivityRepository extends JpaRepository<CrmActivity, Long> {
  List<CrmActivity> findByStudentLeadIdAndDeletedFalseOrderByOccurredAtDesc(Long leadId);
  List<CrmActivity> findByB2bAccountIdAndDeletedFalseOrderByOccurredAtDesc(Long accountId);

  @Query("select activity.type, count(activity) from CrmActivity activity where activity.deleted=false and activity.occurredAt>=:from and activity.occurredAt<:to group by activity.type")
  List<Object[]> countsBetween(@Param("from") Instant from, @Param("to") Instant to);
}
