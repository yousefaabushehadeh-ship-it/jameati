package com.jameati.support;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
  boolean existsByReferenceCode(String referenceCode);

  Page<ContactMessage> findByDeletedFalse(Pageable pageable);
}
