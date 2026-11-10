package com.jameati.integration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntakeLogRepository extends JpaRepository<IntakeLog, Long> {
  Page<IntakeLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
