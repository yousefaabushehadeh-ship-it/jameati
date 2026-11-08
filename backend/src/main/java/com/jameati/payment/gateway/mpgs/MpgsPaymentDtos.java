package com.jameati.payment.gateway.mpgs;

import com.jameati.subscription.MembershipPlanCode;
import com.jameati.subscription.PaymentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public final class MpgsPaymentDtos {
  private MpgsPaymentDtos() {}

  public record CreatePaymentRequest(@NotNull MembershipPlanCode planCode) {}
  public record SessionResponse(String id, String scriptUrl) {}
  public record AuthenticationResponse(String status, String redirectHtml) {}
  public record PaymentResponse(
      Long paymentId, String orderId, String transactionId, PaymentStatus status,
      BigDecimal amount, String currency, SessionResponse session,
      AuthenticationResponse authentication, String gatewayResult, String gatewayResponseCode) {}
  public record SessionRequest(@NotBlank @Size(min = 31, max = 35)
      @Pattern(regexp = "^[\\x20-\\x7E]+$") String sessionId) {}
  public record AuthenticateRequest(
      @NotBlank @Size(min = 31, max = 35) @Pattern(regexp = "^[\\x20-\\x7E]+$") String sessionId,
      @NotNull BrowserDetails browser) {}
  public record BrowserDetails(
      @NotBlank @Size(max = 2048) String userAgent,
      @NotBlank @Size(max = 2048) String acceptHeader,
      @NotBlank @Size(max = 35) String language,
      @Min(1) @Max(10000) int screenWidth,
      @Min(1) @Max(10000) int screenHeight,
      @Min(1) @Max(48) int colorDepth,
      @Min(-840) @Max(840) int timeZone,
      boolean javaEnabled) {}
}
