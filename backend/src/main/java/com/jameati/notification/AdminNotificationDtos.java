package com.jameati.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

public final class AdminNotificationDtos {
  private AdminNotificationDtos() {}

  public record DeliveryRequest(
      @NotBlank(message = "عنوان الإشعار مطلوب")
          @Size(max = 180, message = "عنوان الإشعار أطول من المسموح")
          String title,
      @NotBlank(message = "نص الإشعار مطلوب")
          @Size(max = 1000, message = "نص الإشعار أطول من المسموح")
          String body,
      @Size(max = 500, message = "رابط الإشعار أطول من المسموح")
          @Pattern(
              regexp = "^(?:|/(?!/)[^\\s]*|https://[^\\s]+)$",
              message = "الرابط يجب أن يكون داخليًا أو يبدأ بـ https://")
          String link,
      @NotNull(message = "الجمهور مطلوب") NotificationAudience audience,
      @NotEmpty(message = "اختر قناة إرسال واحدة على الأقل")
          Set<@Valid @NotNull NotificationChannel> channels,
      @Future(message = "موعد الجدولة يجب أن يكون في المستقبل") Instant scheduledAt) {}

  public record DeliveryResponse(
      Long id,
      String title,
      String body,
      String link,
      NotificationAudience audience,
      Set<NotificationChannel> channels,
      NotificationDeliveryStatus status,
      Instant scheduledAt,
      Instant startedAt,
      Instant completedAt,
      int recipientCount,
      int inAppSentCount,
      int emailSentCount,
      int failedCount,
      String errorMessage,
      Long createdBy,
      Instant createdAt,
      Instant updatedAt) {}
}
