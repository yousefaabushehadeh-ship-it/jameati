package com.jameati.subscription;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScholarshipSubscriptionRepository extends JpaRepository<ScholarshipSubscription, Long> {
  Optional<ScholarshipSubscription> findByUserIdAndOpportunityIdAndDeletedFalse(Long userId, Long opportunityId);
  Optional<ScholarshipSubscription> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);
  Page<ScholarshipSubscription> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);
  Page<ScholarshipSubscription> findByDeletedFalse(Pageable pageable);
  Page<ScholarshipSubscription> findByApplicationStatusAndDeletedFalse(ScholarshipApplicationStatus status, Pageable pageable);
}
