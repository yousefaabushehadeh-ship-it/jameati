package com.jameati.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class MembershipDtos {
  private MembershipDtos() {}

  public record MembershipPlanResponse(
      boolean enabled,
      BigDecimal annualPrice,
      String currency,
      int billingPeriodMonths,
      int freeLimit,
      Map<String, List<Long>> freeContent,
      String cliqAlias,
      boolean testPaymentEnabled,
      boolean active,
      boolean authenticated,
      Instant expiresAt,
      List<MembershipPackageResponse> packages) {}

  public record MembershipPackageResponse(
      MembershipPlanCode code, BigDecimal price, String currency, int billingPeriodMonths,
      boolean contentAccess, boolean consultationAccess) {}

  public record MembershipCheckoutRequest(
      @NotBlank @Size(max = 160) String senderName,
      @NotBlank @Size(max = 100) String paymentReference,
      MembershipPlanCode planCode) {
    public MembershipCheckoutRequest(String senderName, String paymentReference) {
      this(senderName, paymentReference, MembershipPlanCode.ANNUAL);
    }
  }

  public record MembershipReceiptCheckoutRequest(
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Email @Size(max = 190) String email,
      @Size(max = 100) String paymentReference,
      MembershipPlanCode planCode) {
    public MembershipReceiptCheckoutRequest(String name, String email, String paymentReference) {
      this(name, email, paymentReference, MembershipPlanCode.ANNUAL);
    }
  }

  public record MembershipRejectionRequest(
      @Size(max = 1000) String reason) {}

  public record MembershipResponse(
      Long id,
      Long userId,
      String userName,
      String userEmail,
      MembershipStatus status,
      BigDecimal amount,
      String currency,
      int billingPeriodMonths,
      String paymentProvider,
      String senderName,
      String paymentReference,
      String applicantName,
      String applicantEmail,
      boolean receiptAttached,
      String receiptUrl,
      String receiptOriginalName,
      String receiptContentType,
      Long receiptSizeBytes,
      Instant receiptUploadedAt,
      boolean autoActivated,
      String activationSource,
      Instant paidAt,
      Instant startsAt,
      Instant expiresAt,
      String rejectionReason,
      String reviewedBy,
      Instant reviewedAt,
      boolean active,
      Instant createdAt,
      Instant updatedAt,
      MembershipPlanCode planCode,
      boolean consultationAccess) {}
}
