package com.jameati.analytics;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {
  long countByOccurredAtBetweenAndDeletedFalse(Instant from, Instant to);

  @Query("select count(distinct e.visitorHash) from AnalyticsEvent e where e.deleted=false and e.occurredAt between :from and :to")
  long uniqueVisitors(Instant from, Instant to);

  @Query("select e.eventType as name, count(e) as total from AnalyticsEvent e where e.deleted=false and e.occurredAt between :from and :to group by e.eventType order by count(e) desc")
  List<CountView> eventCounts(Instant from, Instant to);

  @Query("select e.path as name, count(e) as total from AnalyticsEvent e where e.deleted=false and e.path is not null and e.occurredAt between :from and :to group by e.path order by count(e) desc")
  List<CountView> topPaths(Instant from, Instant to, Pageable pageable);

  interface CountView {
    String getName();
    long getTotal();
  }
}
