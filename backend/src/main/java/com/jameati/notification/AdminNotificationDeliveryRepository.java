package com.jameati.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminNotificationDeliveryRepository
    extends JpaRepository<AdminNotificationDelivery, Long> {
  Page<AdminNotificationDelivery> findByDeletedFalse(Pageable pageable);

  Optional<AdminNotificationDelivery> findByIdAndDeletedFalse(Long id);

  @Query(
      """
      select delivery.id
      from AdminNotificationDelivery delivery
      where delivery.deleted = false
        and (
          (delivery.status = :scheduled and delivery.scheduledAt <= :now)
          or (delivery.status = :sending and delivery.claimedAt <= :staleBefore)
        )
      order by delivery.scheduledAt asc, delivery.id asc
      """)
  List<Long> findClaimableIds(
      @Param("scheduled") NotificationDeliveryStatus scheduled,
      @Param("sending") NotificationDeliveryStatus sending,
      @Param("now") Instant now,
      @Param("staleBefore") Instant staleBefore,
      Pageable pageable);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update AdminNotificationDelivery delivery
      set delivery.status = :sending,
          delivery.claimedAt = :now,
          delivery.startedAt = coalesce(delivery.startedAt, :now),
          delivery.updatedAt = :now
      where delivery.id = :id
        and delivery.deleted = false
        and (
          (delivery.status = :scheduled and delivery.scheduledAt <= :now)
          or (delivery.status = :sending and delivery.claimedAt <= :staleBefore)
        )
      """)
  int claim(
      @Param("id") Long id,
      @Param("scheduled") NotificationDeliveryStatus scheduled,
      @Param("sending") NotificationDeliveryStatus sending,
      @Param("now") Instant now,
      @Param("staleBefore") Instant staleBefore);
}
