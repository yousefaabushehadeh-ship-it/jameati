package com.jameati.notification;

import com.jameati.common.NotFoundException;
import com.jameati.me.UserNotification;
import com.jameati.me.UserNotificationRepository;
import com.jameati.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminNotificationRecipientService {
  private static final Duration STALE_RECIPIENT_AFTER = Duration.ofMinutes(15);

  private final AdminNotificationRecipientRepository recipients;
  private final UserNotificationRepository notifications;
  private final UserRepository users;

  public AdminNotificationRecipientService(
      AdminNotificationRecipientRepository recipients,
      UserNotificationRepository notifications,
      UserRepository users) {
    this.recipients = recipients;
    this.notifications = notifications;
    this.users = users;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long ensureRecipient(
      Long deliveryId, Long userId, boolean inAppEnabled, boolean emailEnabled) {
    return recipients
        .findByDeliveryIdAndUserIdAndDeletedFalse(deliveryId, userId)
        .map(AdminNotificationRecipient::getId)
        .orElseGet(
            () -> {
              var recipient = new AdminNotificationRecipient();
              recipient.setDeliveryId(deliveryId);
              recipient.setUserId(userId);
              recipient.setInAppStatus(
                  inAppEnabled ? RecipientChannelStatus.PENDING : RecipientChannelStatus.SKIPPED);
              recipient.setEmailStatus(
                  emailEnabled ? RecipientChannelStatus.PENDING : RecipientChannelStatus.SKIPPED);
              return recipients.saveAndFlush(recipient).getId();
            });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void deliverInApp(
      Long recipientId, Long deliveryId, String title, String body, String link) {
    var recipient = locked(recipientId);
    if (recipient.getInAppStatus() == RecipientChannelStatus.SENT
        || recipient.getInAppStatus() == RecipientChannelStatus.SKIPPED) {
      return;
    }
    var user = requireEnabledUser(recipient.getUserId());
    var notificationType = notificationType(deliveryId);
    if (!notifications.existsByUserIdAndTypeAndDeletedFalse(user.getId(), notificationType)) {
      var notification = new UserNotification();
      notification.setUser(user);
      notification.setType(notificationType);
      notification.setTitle(title);
      notification.setBody(body);
      notification.setLink(link);
      notifications.saveAndFlush(notification);
    }
    recipient.setInAppStatus(RecipientChannelStatus.SENT);
    recipient.setInAppError(null);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markInAppFailed(Long recipientId, String error) {
    var recipient = locked(recipientId);
    if (recipient.getInAppStatus() != RecipientChannelStatus.SENT) {
      recipient.setInAppStatus(RecipientChannelStatus.FAILED);
      recipient.setInAppError(AdminNotificationDeliveryStateService.safeMessage(error));
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean beginEmail(Long recipientId, Instant now) {
    var recipient = locked(recipientId);
    if (recipient.getEmailStatus() == RecipientChannelStatus.SENT
        || recipient.getEmailStatus() == RecipientChannelStatus.SKIPPED) {
      return false;
    }
    if (recipient.getEmailStatus() == RecipientChannelStatus.PROCESSING
        && recipient.getUpdatedAt() != null
        && recipient.getUpdatedAt().isAfter(now.minus(STALE_RECIPIENT_AFTER))) {
      return false;
    }
    recipient.setEmailStatus(RecipientChannelStatus.PROCESSING);
    recipient.setEmailError(null);
    recipients.saveAndFlush(recipient);
    return true;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markEmailSent(Long recipientId) {
    var recipient = locked(recipientId);
    recipient.setEmailStatus(RecipientChannelStatus.SENT);
    recipient.setEmailError(null);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markEmailFailed(Long recipientId, String error) {
    var recipient = locked(recipientId);
    if (recipient.getEmailStatus() != RecipientChannelStatus.SENT) {
      recipient.setEmailStatus(RecipientChannelStatus.FAILED);
      recipient.setEmailError(AdminNotificationDeliveryStateService.safeMessage(error));
    }
  }

  private AdminNotificationRecipient locked(Long id) {
    return recipients
        .findLockedById(id)
        .orElseThrow(() -> new NotFoundException("سجل مستلم الإشعار غير موجود"));
  }

  private com.jameati.user.User requireEnabledUser(Long id) {
    var user =
        users
            .findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new NotFoundException("مستلم الإشعار غير موجود"));
    if (!user.isEnabled()) throw new IllegalArgumentException("حساب المستلم معطل");
    return user;
  }

  private static String notificationType(Long deliveryId) {
    return "ADMIN_BROADCAST_" + deliveryId;
  }
}
