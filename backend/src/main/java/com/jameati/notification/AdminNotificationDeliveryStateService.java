package com.jameati.notification;

import static com.jameati.notification.AdminNotificationDtos.DeliveryRequest;
import static com.jameati.notification.AdminNotificationDtos.DeliveryResponse;

import com.jameati.common.NotFoundException;
import com.jameati.user.User;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class AdminNotificationDeliveryStateService {
  private final AdminNotificationDeliveryRepository deliveries;
  private final AdminNotificationRecipientRepository recipients;

  public AdminNotificationDeliveryStateService(
      AdminNotificationDeliveryRepository deliveries,
      AdminNotificationRecipientRepository recipients) {
    this.deliveries = deliveries;
    this.recipients = recipients;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long create(User creator, DeliveryRequest request, Instant now) {
    var delivery = new AdminNotificationDelivery();
    delivery.setCreatedBy(creator);
    delivery.setTitle(request.title().trim());
    delivery.setBody(request.body().trim());
    delivery.setLink(clean(request.link()));
    delivery.setAudience(request.audience());
    delivery.setInAppEnabled(request.channels().contains(NotificationChannel.IN_APP));
    delivery.setEmailEnabled(request.channels().contains(NotificationChannel.EMAIL));
    delivery.setStatus(NotificationDeliveryStatus.SCHEDULED);
    delivery.setScheduledAt(request.scheduledAt() == null ? now : request.scheduledAt());
    return deliveries.saveAndFlush(delivery).getId();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean claim(Long id, Instant now, Instant staleBefore) {
    return deliveries.claim(
            id,
            NotificationDeliveryStatus.SCHEDULED,
            NotificationDeliveryStatus.SENDING,
            now,
            staleBefore)
        == 1;
  }

  @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
  public DeliveryPlan plan(Long id) {
    var delivery = require(id);
    return new DeliveryPlan(
        delivery.getId(),
        delivery.getTitle(),
        delivery.getBody(),
        delivery.getLink(),
        delivery.getAudience(),
        delivery.isInAppEnabled(),
        delivery.isEmailEnabled());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void heartbeat(Long id, Instant now) {
    var delivery = require(id);
    if (delivery.getStatus() == NotificationDeliveryStatus.SENDING) {
      delivery.setClaimedAt(now);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void complete(Long id, Instant now, String firstError) {
    var delivery = require(id);
    var recipientCount = recipients.countByDeliveryIdAndDeletedFalse(id);
    var inAppSent =
        recipients.countByDeliveryIdAndInAppStatusAndDeletedFalse(
            id, RecipientChannelStatus.SENT);
    var emailSent =
        recipients.countByDeliveryIdAndEmailStatusAndDeletedFalse(
            id, RecipientChannelStatus.SENT);
    var inAppFailed =
        recipients.countByDeliveryIdAndInAppStatusAndDeletedFalse(
            id, RecipientChannelStatus.FAILED);
    var emailFailed =
        recipients.countByDeliveryIdAndEmailStatusAndDeletedFalse(
            id, RecipientChannelStatus.FAILED);
    var failed = inAppFailed + emailFailed;
    var sent = inAppSent + emailSent;

    delivery.setRecipientCount(safeInt(recipientCount));
    delivery.setInAppSentCount(safeInt(inAppSent));
    delivery.setEmailSentCount(safeInt(emailSent));
    delivery.setFailedCount(safeInt(failed));
    delivery.setCompletedAt(now);
    delivery.setErrorMessage(failed == 0 ? null : safeMessage(firstError));
    delivery.setStatus(
        failed == 0
            ? NotificationDeliveryStatus.SENT
            : sent == 0
                ? NotificationDeliveryStatus.FAILED
                : NotificationDeliveryStatus.COMPLETED_WITH_ERRORS);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void fail(Long id, Instant now, RuntimeException exception) {
    deliveries
        .findByIdAndDeletedFalse(id)
        .ifPresent(
            delivery -> {
              delivery.setRecipientCount(
                  safeInt(recipients.countByDeliveryIdAndDeletedFalse(id)));
              delivery.setInAppSentCount(
                  safeInt(
                      recipients.countByDeliveryIdAndInAppStatusAndDeletedFalse(
                          id, RecipientChannelStatus.SENT)));
              delivery.setEmailSentCount(
                  safeInt(
                      recipients.countByDeliveryIdAndEmailStatusAndDeletedFalse(
                          id, RecipientChannelStatus.SENT)));
              delivery.setFailedCount(
                  safeInt(
                      recipients.countByDeliveryIdAndInAppStatusAndDeletedFalse(
                              id, RecipientChannelStatus.FAILED)
                          + recipients.countByDeliveryIdAndEmailStatusAndDeletedFalse(
                              id, RecipientChannelStatus.FAILED)));
              delivery.setStatus(NotificationDeliveryStatus.FAILED);
              delivery.setCompletedAt(now);
              delivery.setErrorMessage(safeMessage(exception.getMessage()));
            });
  }

  @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
  public DeliveryResponse response(Long id) {
    return toResponse(require(id));
  }

  public static DeliveryResponse toResponse(AdminNotificationDelivery delivery) {
    Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
    if (delivery.isInAppEnabled()) channels.add(NotificationChannel.IN_APP);
    if (delivery.isEmailEnabled()) channels.add(NotificationChannel.EMAIL);
    return new DeliveryResponse(
        delivery.getId(),
        delivery.getTitle(),
        delivery.getBody(),
        delivery.getLink(),
        delivery.getAudience(),
        channels,
        delivery.getStatus(),
        delivery.getScheduledAt(),
        delivery.getStartedAt(),
        delivery.getCompletedAt(),
        delivery.getRecipientCount(),
        delivery.getInAppSentCount(),
        delivery.getEmailSentCount(),
        delivery.getFailedCount(),
        delivery.getErrorMessage(),
        delivery.getCreatedBy().getId(),
        delivery.getCreatedAt(),
        delivery.getUpdatedAt());
  }

  private AdminNotificationDelivery require(Long id) {
    return deliveries
        .findByIdAndDeletedFalse(id)
        .orElseThrow(() -> new NotFoundException("عملية الإشعار غير موجودة"));
  }

  private static String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  static String safeMessage(String value) {
    var message = value == null || value.isBlank() ? "تعذر إكمال بعض عمليات التسليم" : value;
    return message.length() > 1000 ? message.substring(0, 1000) : message;
  }

  private static int safeInt(long value) {
    return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
  }

  public record DeliveryPlan(
      Long id,
      String title,
      String body,
      String link,
      NotificationAudience audience,
      boolean inAppEnabled,
      boolean emailEnabled) {}
}
