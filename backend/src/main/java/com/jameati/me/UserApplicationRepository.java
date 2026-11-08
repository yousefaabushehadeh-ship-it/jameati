package com.jameati.me;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserApplicationRepository extends JpaRepository<UserApplication, Long> {
  Page<UserApplication> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);

  Optional<UserApplication> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

  Optional<UserApplication> findByUserIdAndOpportunityId(Long userId, Long opportunityId);
}
