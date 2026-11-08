package com.jameati.notification;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminNotificationRecipientRepository
    extends JpaRepository<AdminNotificationRecipient, Long> {
  Optional<AdminNotificationRecipient> findByDeliveryIdAndUserIdAndDeletedFalse(
      Long deliveryId, Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select recipient from AdminNotificationRecipient recipient where recipient.id = :id and recipient.deleted = false")
  Optional<AdminNotificationRecipient> findLockedById(@Param("id") Long id);

  long countByDeliveryIdAndDeletedFalse(Long deliveryId);

  long countByDeliveryIdAndInAppStatusAndDeletedFalse(
      Long deliveryId, RecipientChannelStatus status);

  long countByDeliveryIdAndEmailStatusAndDeletedFalse(
      Long deliveryId, RecipientChannelStatus status);
}
