package com.jameati.support;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

public final class SupportDtos {
  private SupportDtos() {}

  public record ContactRequest(
      @NotBlank(message = "اكتب اسمك")
          @Size(max = 80, message = "الاسم أطول من الحد المسموح")
          String name,
      @NotBlank(message = "أدخل بريدك الإلكتروني")
          @Email(message = "البريد الإلكتروني غير صالح")
          @Size(max = 190, message = "البريد الإلكتروني أطول من الحد المسموح")
          String email,
      @NotBlank(message = "اختر موضوع الرسالة")
          @Pattern(
              regexp = "support|content|partnership|privacy|other",
              message = "موضوع الرسالة غير صالح")
          String subject,
      @Size(max = 500, message = "رابط الصفحة أطول من الحد المسموح")
          @Pattern(regexp = "^$|https?://.+", message = "رابط الصفحة يجب أن يبدأ بـ http أو https")
          String pageUrl,
      @NotBlank(message = "اكتب تفاصيل الرسالة")
          @Size(min = 20, max = 2000, message = "يجب أن تكون الرسالة بين 20 و2000 حرف")
          String message,
      @NotNull(message = "يجب الموافقة على معالجة البيانات")
          @AssertTrue(message = "يجب الموافقة على معالجة البيانات")
          Boolean consent) {}

  public record ContactReceipt(String referenceCode, String status, Instant submittedAt) {}

  public record NewsletterRequest(
      @NotBlank(message = "أدخل بريدك الإلكتروني")
          @Email(message = "البريد الإلكتروني غير صالح")
          @Size(max = 190, message = "البريد الإلكتروني أطول من الحد المسموح")
          String email,
      @NotBlank(message = "اختر وتيرة الرسائل")
          @Pattern(
              regexp = "weekly|twice-monthly|deadlines",
              message = "وتيرة الرسائل غير صالحة")
          String frequency,
      @NotEmpty(message = "اختر موضوعًا واحدًا على الأقل")
          @Size(max = 4, message = "عدد المواضيع أكبر من الحد المسموح")
          Set<
                  @Pattern(
                      regexp = "opportunities|courses|jobs|guides",
                      message = "أحد مواضيع النشرة غير صالح")
                  String>
              topics,
      @NotNull(message = "يجب الموافقة على استلام النشرة")
          @AssertTrue(message = "يجب الموافقة على استلام النشرة")
          Boolean consent) {}

  public record NewsletterResponse(
      String email,
      String frequency,
      Set<String> topics,
      boolean active,
      Instant subscribedAt,
      Instant updatedAt) {}

  public record UnsubscribeResponse(String email, boolean active) {}

  public record ContactAdminResponse(
      Long id,
      String referenceCode,
      String name,
      String email,
      String subject,
      String pageUrl,
      String message,
      String status,
      Instant createdAt) {}

  public record NewsletterAdminResponse(
      Long id,
      String email,
      String frequency,
      Set<String> topics,
      Instant subscribedAt,
      Instant updatedAt) {}
}
