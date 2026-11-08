package com.jameati.email;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface NewsletterCampaignRepository
    extends JpaRepository<NewsletterCampaign, Long> {
  Page<NewsletterCampaign> findByDeletedFalse(Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<NewsletterCampaign> findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
      NewsletterCampaignStatus status, Instant scheduledAt);
}
