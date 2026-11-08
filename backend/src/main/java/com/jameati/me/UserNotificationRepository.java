package com.jameati.me;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
  Page<UserNotification> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);

  Optional<UserNotification> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

  long countByUserIdAndDeletedFalseAndReadAtIsNull(Long userId);

  List<UserNotification> findByUserIdAndDeletedFalseAndReadAtIsNull(Long userId);

  boolean existsByUserIdAndTypeAndDeletedFalse(Long userId, String type);
}
