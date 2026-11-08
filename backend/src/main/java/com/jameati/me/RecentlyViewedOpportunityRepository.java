package com.jameati.me;

import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecentlyViewedOpportunityRepository
    extends JpaRepository<RecentlyViewedOpportunity, Long> {
  // The unique key arbitrates concurrent first views across all server instances.
  @Modifying(flushAutomatically = true)
  @Query(value = """
      INSERT INTO recently_viewed_opportunities
        (user_id, opportunity_id, viewed_at, created_at, updated_at, deleted)
      VALUES (:userId, :opportunityId, :viewedAt, :viewedAt, :viewedAt, false)
      ON DUPLICATE KEY UPDATE
        viewed_at = GREATEST(viewed_at, :viewedAt),
        updated_at = GREATEST(updated_at, :viewedAt), deleted = false
      """, nativeQuery = true)
  void recordView(@Param("userId") Long userId, @Param("opportunityId") Long opportunityId,
      @Param("viewedAt") Instant viewedAt);

  List<RecentlyViewedOpportunity>
      findTop50ByUserIdAndDeletedFalseOrderByViewedAtDesc(Long userId);

  List<RecentlyViewedOpportunity> findByUserIdAndDeletedFalse(Long userId);

  Optional<RecentlyViewedOpportunity> findByUserIdAndOpportunityId(
      Long userId, Long opportunityId);
}
