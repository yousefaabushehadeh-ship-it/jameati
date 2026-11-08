package com.jameati.subscription;

import com.jameati.common.BaseEntity;
import com.jameati.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "annual_memberships", indexes = {
    @Index(name = "idx_annual_membership_user_history", columnList = "user_id,deleted,created_at"),
    @Index(name = "idx_annual_membership_active", columnList = "user_id,status,starts_at,expires_at,deleted"),
    @Index(name = "idx_annual_membership_admin", columnList = "status,deleted,updated_at")})
@Getter
@Setter
@NoArgsConstructor
public class AnnualMembership extends BaseEntity {
  @Version
  @Column(nullable = false)
  private long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MembershipPlanCode planCode = MembershipPlanCode.ANNUAL;

  @Column(nullable = false)
  private int billingPeriodMonths = 12;

  @Column(length = 30, nullable = false)
  private String consultationProgress = "WAITING";

  @Column(length = 4000)
  private String consultationNotes;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MembershipStatus status = MembershipStatus.PENDING;

  @Column(nullable = false, length = 40)
  private String paymentProvider = "MOCK";

  @Column(length = 100)
  private String paymentReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_status", nullable = false, length = 30)
  private PaymentStatus paymentStatus = PaymentStatus.PENDING;

  @Column(name = "gateway_order_id", length = 40, unique = true)
  private String gatewayOrderId;

  @Column(name = "gateway_authentication_transaction_id", length = 50, unique = true)
  private String gatewayAuthenticationTransactionId;

  @Column(name = "gateway_transaction_id", length = 50, unique = true)
  private String gatewayTransactionId;

  @Column(name = "gateway_session_id", length = 64)
  private String gatewaySessionId;

  @Column(name = "gateway_result", length = 40)
  private String gatewayResult;

  @Column(name = "gateway_authentication_status", length = 80)
  private String gatewayAuthenticationStatus;

  @Column(name = "gateway_authentication_version", length = 20)
  private String gatewayAuthenticationVersion;

  @Column(name = "gateway_authentication_stage", length = 30)
  private String gatewayAuthenticationStage;

  @Column(name = "gateway_authentication_html", columnDefinition = "TEXT")
  private String gatewayAuthenticationHtml;

  @Column(name = "gateway_response_code", length = 80)
  private String gatewayResponseCode;

  @Column(name = "payment_sender_name", length = 160)
  private String paymentSenderName;

  @Column(name = "applicant_name", length = 160)
  private String applicantName;

  @Column(name = "applicant_email", length = 190)
  private String applicantEmail;

  @Column(name = "receipt_original_name", length = 255)
  private String receiptOriginalName;

  @Column(name = "receipt_stored_name", length = 255, unique = true)
  private String receiptStoredName;

  @Column(name = "receipt_content_type", length = 40)
  private String receiptContentType;

  @Column(name = "receipt_size_bytes")
  private Long receiptSizeBytes;

  @Column(name = "receipt_sha256", length = 64, unique = true)
  private String receiptSha256;

  @Column(name = "receipt_uploaded_at")
  private Instant receiptUploadedAt;

  @Column(name = "auto_activated", nullable = false)
  private boolean autoActivated;

  @Column(name = "activation_source", length = 40)
  private String activationSource;

  @Column(name = "rejection_reason", length = 1000)
  private String rejectionReason;

  @Column(name = "reviewed_by", length = 190)
  private String reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  private Instant paidAt;
  private Instant startsAt;
  private Instant expiresAt;
}
