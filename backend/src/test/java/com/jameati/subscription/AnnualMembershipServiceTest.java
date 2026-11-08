package com.jameati.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jameati.me.UserNotification;
import com.jameati.me.UserNotificationRepository;
import com.jameati.subscription.MembershipDtos.MembershipCheckoutRequest;
import com.jameati.subscription.MembershipDtos.MembershipReceiptCheckoutRequest;
import com.jameati.subscription.MembershipDtos.MembershipRejectionRequest;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class AnnualMembershipServiceTest {
  private AnnualMembershipRepository memberships;
  private SubscriptionPlanService plans;
  private UserRepository users;
  private UserNotificationRepository notifications;
  private MembershipReceiptStorageService receiptStorage;
  private AnnualMembershipService service;
  private User user;

  @BeforeEach
  void setUp() {
    memberships = mock(AnnualMembershipRepository.class);
    plans = mock(SubscriptionPlanService.class);
    users = mock(UserRepository.class);
    notifications = mock(UserNotificationRepository.class);
    receiptStorage = mock(MembershipReceiptStorageService.class);
    service = new AnnualMembershipService(
        memberships, plans, users, notifications, receiptStorage, true);
    user = new User("student@example.com", "hash", "Student");
    user.setId(7L);
    when(users.findEnabledByIdForUpdate(7L)).thenReturn(Optional.of(user));
    when(memberships.save(any(AnnualMembership.class))).thenAnswer(invocation -> {
      var item = invocation.getArgument(0, AnnualMembership.class);
      if (item.getId() == null) item.setId(91L);
      return item;
    });
  }

  @Test
  void publicPlanOffersOnlyAnnualAndConsultationAtCurrentPrices() {
    var response = service.plan(new ContentAccessService.AccessContext(false, false, null, plan(true)), false);
    assertThat(response.annualPrice()).isEqualByComparingTo("25.00");
    assertThat(response.packages()).extracting(MembershipDtos.MembershipPackageResponse::code)
        .containsExactly(MembershipPlanCode.ANNUAL, MembershipPlanCode.CONSULTATION);
    assertThat(response.packages().getFirst().price()).isEqualByComparingTo("25.00");
  }

  @Test
  void quarterlyCheckoutIsUnavailableButExistingRequestCanStillBeApproved() {
    when(plans.current()).thenReturn(plan(true));
    assertThatThrownBy(() -> service.checkout(user,
        new MembershipCheckoutRequest("Student", "Q-001", MembershipPlanCode.QUARTERLY)))
        .isInstanceOf(IllegalArgumentException.class);
    var image = new MockMultipartFile("receiptImage", "receipt.png", "image/png", new byte[] {1});
    assertThatThrownBy(() -> service.checkoutWithReceipt(user,
        new MembershipReceiptCheckoutRequest("Student", user.getEmail(), null,
            MembershipPlanCode.QUARTERLY), image))
        .isInstanceOf(IllegalArgumentException.class);
    verify(memberships, never()).save(any(AnnualMembership.class));

    var existing = new AnnualMembership();
    existing.setId(91L);
    existing.setUser(user);
    existing.setPlanCode(MembershipPlanCode.QUARTERLY);
    existing.setAmount(new BigDecimal("20.00"));
    existing.setCurrency("JOD");
    existing.setBillingPeriodMonths(3);
    existing.setStatus(MembershipStatus.PENDING);
    existing.setPaymentProvider("CLIQ");
    when(memberships.findById(91L)).thenReturn(Optional.of(existing));
    when(memberships.findByIdForUpdate(91L)).thenReturn(Optional.of(existing));
    var paid = service.approve(91L, "admin@example.com");
    assertThat(paid.expiresAt()).isEqualTo(paid.startsAt().atZone(ZoneOffset.UTC).plusMonths(3).toInstant());
  }

  @Test
  void consultationReceiptWaitsForApprovalEvenWhenContentAutoActivationIsEnabled() {
    when(plans.current()).thenReturn(plan(true));
    var image = new MockMultipartFile("receiptImage", "receipt.png", "image/png", new byte[] {1, 2, 3});
    when(receiptStorage.store(image)).thenReturn(new MembershipReceiptStorageService.StoredReceipt(
        "consultation.png", "receipt.png", "image/png", 3, "d".repeat(64)));
    var response = service.checkoutWithReceipt(user, new MembershipReceiptCheckoutRequest(
        "Student", user.getEmail(), null, MembershipPlanCode.CONSULTATION), image);
    assertThat(response.amount()).isEqualByComparingTo("150.00");
    assertThat(response.currency()).isEqualTo("JOD");
    assertThat(response.planCode()).isEqualTo(MembershipPlanCode.CONSULTATION);
    assertThat(response.billingPeriodMonths()).isZero();
    assertThat(response.status()).isEqualTo(MembershipStatus.PENDING);
    assertThat(response.consultationAccess()).isFalse();
    assertThat(response.autoActivated()).isFalse();
    var captured = ArgumentCaptor.forClass(AnnualMembership.class);
    verify(memberships).save(captured.capture());
    when(memberships.findById(91L)).thenReturn(Optional.of(captured.getValue()));
    when(memberships.findByIdForUpdate(91L)).thenReturn(Optional.of(captured.getValue()));
    var paid = service.approve(91L, "admin@example.com");
    assertThat(paid.status()).isEqualTo(MembershipStatus.PAID);
    assertThat(paid.consultationAccess()).isTrue();
    assertThat(paid.expiresAt()).isNull();
  }

  @Test
  void consultationCanBePurchasedAlongsideAnExistingContentMembership() {
    when(plans.current()).thenReturn(plan(true));
    var existing = membership(MembershipStatus.PAID, "CLIQ");
    existing.setStartsAt(Instant.now().minusSeconds(60));
    existing.setExpiresAt(Instant.now().plusSeconds(86400));
    when(memberships.findFirstByUserIdAndStatusAndStartsAtLessThanEqualAndExpiresAtAfterAndDeletedFalseOrderByExpiresAtDesc(
        eq(7L), eq(MembershipStatus.PAID), any(), any())).thenReturn(Optional.of(existing));
    var response = service.checkout(user, new MembershipCheckoutRequest("Student", "C-001", MembershipPlanCode.CONSULTATION));
    assertThat(response.status()).isEqualTo(MembershipStatus.PENDING);
    assertThat(response.planCode()).isEqualTo(MembershipPlanCode.CONSULTATION);
    assertThat(response.amount()).isEqualByComparingTo("150.00");
  }

  @Test
  void checkoutCreatesPendingCliqRequestFromCurrentPlanAndNormalizesTransferDetails() {
    when(plans.current()).thenReturn(plan(true));
    when(memberships
        .findFirstByUserIdAndStatusAndStartsAtLessThanEqualAndExpiresAtAfterAndDeletedFalseOrderByExpiresAtDesc(
            eq(7L), eq(MembershipStatus.PAID), any(), any()))
        .thenReturn(Optional.empty());
    when(memberships
        .findFirstByUserIdAndPlanCodeAndStatusAndPaymentProviderAndDeletedFalseOrderByCreatedAtDesc(
            7L, MembershipPlanCode.ANNUAL, MembershipStatus.PENDING, "CLIQ"))
        .thenReturn(Optional.empty());

    var response = service.checkout(
        user, new MembershipCheckoutRequest("  Ahmad Student  ", "  CLIQ-2026-001  "));

    assertThat(response.id()).isEqualTo(91L);
    assertThat(response.status()).isEqualTo(MembershipStatus.PENDING);
    assertThat(response.amount()).isEqualByComparingTo("25.00");
    assertThat(response.currency()).isEqualTo("JOD");
    assertThat(response.billingPeriodMonths()).isEqualTo(12);
    assertThat(response.paymentProvider()).isEqualTo("CLIQ");
    assertThat(response.senderName()).isEqualTo("Ahmad Student");
    assertThat(response.paymentReference()).isEqualTo("CLIQ-2026-001");
    assertThat(response.active()).isFalse();
    assertThat(response.paidAt()).isNull();
    assertThat(response.startsAt()).isNull();
    assertThat(response.expiresAt()).isNull();
  }

  @Test
  void checkoutRetryReturnsOldPendingCliqRequestWithoutThirtyMinuteExpiry() {
    when(plans.current()).thenReturn(plan(true));
    var pending = membership(MembershipStatus.PENDING, "CLIQ");
    pending.setCreatedAt(Instant.now().minusSeconds(2 * 24 * 60 * 60));
    pending.setPaymentSenderName("Original Sender");
    pending.setPaymentReference("ORIGINAL-REFERENCE");
    when(memberships
        .findFirstByUserIdAndPlanCodeAndStatusAndPaymentProviderAndDeletedFalseOrderByCreatedAtDesc(
            7L, MembershipPlanCode.ANNUAL, MembershipStatus.PENDING, "CLIQ"))
        .thenReturn(Optional.of(pending));

    var response = service.checkout(
        user, new MembershipCheckoutRequest("Different Sender", "DIFFERENT-REFERENCE"));

    assertThat(response.id()).isEqualTo(91L);
    assertThat(response.senderName()).isEqualTo("Original Sender");
    assertThat(response.paymentReference()).isEqualTo("ORIGINAL-REFERENCE");
    verify(memberships, never()).save(any());
  }

  @Test
  void receiptCheckoutImmediatelyActivatesMembershipAndKeepsAuditMetadata() {
    when(plans.current()).thenReturn(plan(true));
    when(memberships
        .findFirstByUserIdAndStatusAndStartsAtLessThanEqualAndExpiresAtAfterAndDeletedFalseOrderByExpiresAtDesc(
            eq(7L), eq(MembershipStatus.PAID), any(), any()))
        .thenReturn(Optional.empty());
    when(memberships
        .findFirstByUserIdAndPlanCodeAndStatusAndPaymentProviderAndDeletedFalseOrderByCreatedAtDesc(
            7L, MembershipPlanCode.ANNUAL, MembershipStatus.PENDING, "CLIQ"))
        .thenReturn(Optional.empty());
    var image = new MockMultipartFile(
        "receiptImage", "receipt.png", "image/png", new byte[] {1, 2, 3});
    var stored = new MembershipReceiptStorageService.StoredReceipt(
        "123e4567-e89b-12d3-a456-426614174000.png",
        "receipt.png",
        "image/png",
        3,
        "a".repeat(64));
    when(receiptStorage.store(image)).thenReturn(stored);
    when(memberships.findFirstByReceiptSha256(stored.sha256())).thenReturn(Optional.empty());
    var earliestActivation = Instant.now();

    var response = service.checkoutWithReceipt(
        user,
        new MembershipReceiptCheckoutRequest(
            "  Ahmad Student  ", " STUDENT@example.com ", " CLIQ-2026-RECEIPT "),
        image);

    assertThat(response.status()).isEqualTo(MembershipStatus.PAID);
    assertThat(response.active()).isTrue();
    assertThat(response.applicantName()).isEqualTo("Ahmad Student");
    assertThat(response.applicantEmail()).isEqualTo("student@example.com");
    assertThat(response.paymentReference()).isEqualTo("CLIQ-2026-RECEIPT");
    assertThat(response.receiptAttached()).isTrue();
    assertThat(response.receiptOriginalName()).isEqualTo("receipt.png");
    assertThat(response.receiptContentType()).isEqualTo("image/png");
    assertThat(response.receiptSizeBytes()).isEqualTo(3L);
    assertThat(response.receiptUploadedAt()).isNotNull();
    assertThat(response.autoActivated()).isTrue();
    assertThat(response.activationSource()).isEqualTo("AUTO_RECEIPT");
    assertThat(response.reviewedBy()).isEqualTo("AUTO_RECEIPT");
    assertThat(response.startsAt()).isAfterOrEqualTo(earliestActivation);
    assertThat(response.expiresAt()).isEqualTo(
        response.startsAt().atZone(ZoneOffset.UTC).plusMonths(12).toInstant());
    verify(notifications).save(argThat(notification ->
        "MEMBERSHIP_AUTO_ACTIVATED".equals(notification.getType())));
  }

  @Test
  void receiptCheckoutUpgradesAnExistingPendingCliqRequest() {
    when(plans.current()).thenReturn(plan(true));
    var pending = membership(MembershipStatus.PENDING, "CLIQ");
    pending.setPaymentReference("OLD-REFERENCE");
    when(memberships
        .findFirstByUserIdAndPlanCodeAndStatusAndPaymentProviderAndDeletedFalseOrderByCreatedAtDesc(
            7L, MembershipPlanCode.ANNUAL, MembershipStatus.PENDING, "CLIQ"))
        .thenReturn(Optional.of(pending));
    var image = new MockMultipartFile(
        "receiptImage", "receipt.jpg", "image/jpeg", new byte[] {1, 2, 3});
    var stored = new MembershipReceiptStorageService.StoredReceipt(
        "123e4567-e89b-12d3-a456-426614174001.jpg",
        "receipt.jpg",
        "image/jpeg",
        3,
        "b".repeat(64));
    when(receiptStorage.store(image)).thenReturn(stored);
    when(memberships.findFirstByReceiptSha256(stored.sha256())).thenReturn(Optional.empty());

    var response = service.checkoutWithReceipt(
        user,
        new MembershipReceiptCheckoutRequest("Ahmad Student", user.getEmail(), null),
        image);

    assertThat(response.id()).isEqualTo(91L);
    assertThat(response.status()).isEqualTo(MembershipStatus.PAID);
    assertThat(response.paymentReference()).isEqualTo("OLD-REFERENCE");
    verify(memberships).save(same(pending));
  }

  @Test
  void receiptCheckoutRejectsAnEmailThatDoesNotBelongToTheAuthenticatedAccount() {
    when(plans.current()).thenReturn(plan(true));
    var image = new MockMultipartFile(
        "receiptImage", "receipt.png", "image/png", new byte[] {1, 2, 3});

    assertThatThrownBy(() -> service.checkoutWithReceipt(
        user,
        new MembershipReceiptCheckoutRequest("Ahmad Student", "other@example.com", null),
        image))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("يطابق بريد الحساب");

    verifyNoInteractions(receiptStorage);
    verify(memberships, never()).save(any());
  }

  @Test
  void receiptCheckoutRejectsAReceiptAlreadyUsedByAnotherMembership() {
    when(plans.current()).thenReturn(plan(true));
    var image = new MockMultipartFile(
        "receiptImage", "receipt.webp", "image/webp", new byte[] {1, 2, 3});
    var stored = new MembershipReceiptStorageService.StoredReceipt(
        "123e4567-e89b-12d3-a456-426614174002.webp",
        "receipt.webp",
        "image/webp",
        3,
        "c".repeat(64));
    var duplicate = membership(MembershipStatus.PAID, "CLIQ");
    duplicate.setId(500L);
    when(receiptStorage.store(image)).thenReturn(stored);
    when(memberships.findFirstByReceiptSha256(stored.sha256()))
        .thenReturn(Optional.of(duplicate));

    assertThatThrownBy(() -> service.checkoutWithReceipt(
        user,
        new MembershipReceiptCheckoutRequest("Ahmad Student", user.getEmail(), null),
        image))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("مستخدمة");

    verify(receiptStorage).discard(stored);
    verify(memberships, never()).save(any());
  }

  @Test
  void mockPaymentCannotActivateCliqRequest() {
    var pending = membership(MembershipStatus.PENDING, "CLIQ");
    pending.setCreatedAt(Instant.now());
    when(memberships.findByIdAndUserIdAndDeletedFalse(91L, 7L)).thenReturn(Optional.of(pending));

    assertThatThrownBy(() -> service.mockPay(user, 91L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("مراجعة واعتماد");

    assertThat(pending.getStatus()).isEqualTo(MembershipStatus.PENDING);
    assertThat(pending.getPaidAt()).isNull();
  }

  @Test
  void mockPaymentStillActivatesFreshLegacyMockRequest() {
    when(plans.current()).thenReturn(new SubscriptionPlanService.PlanConfig(
        true, new BigDecimal("175.00"), "EUR", 12, 4, Map.of(), "JAMEATI.CLIQ"));
    var pending = membership(MembershipStatus.PENDING, "MOCK");
    pending.setCreatedAt(Instant.now());
    when(memberships.findByIdAndUserIdAndDeletedFalse(91L, 7L)).thenReturn(Optional.of(pending));

    var response = service.mockPay(user, 91L);

    assertThat(response.status()).isEqualTo(MembershipStatus.PAID);
    assertThat(response.active()).isTrue();
    assertThat(response.amount()).isEqualByComparingTo("120.00");
    assertThat(response.currency()).isEqualTo("USD");
    assertThat(response.paymentReference()).startsWith("MOCK-");
    assertThat(response.expiresAt()).isEqualTo(
        response.startsAt().atZone(ZoneOffset.UTC).plusMonths(12).toInstant());
  }

  @Test
  void adminApprovalActivatesCliqRequestForTwelveMonthsFromServerReviewTime() {
    var pending = membership(MembershipStatus.PENDING, "CLIQ");
    pending.setCreatedAt(Instant.now().minusSeconds(3 * 24 * 60 * 60));
    pending.setPaymentSenderName("Ahmad Student");
    pending.setPaymentReference("CLIQ-2026-001");
    when(memberships.findById(91L)).thenReturn(Optional.of(pending));
    when(memberships.findByIdForUpdate(91L)).thenReturn(Optional.of(pending));
    var earliestReviewTime = Instant.now();

    var response = service.approve(91L, "admin@example.com");

    assertThat(response.status()).isEqualTo(MembershipStatus.PAID);
    assertThat(response.active()).isTrue();
    assertThat(response.paidAt()).isEqualTo(response.startsAt());
    assertThat(response.reviewedAt()).isEqualTo(response.startsAt());
    assertThat(response.startsAt()).isAfterOrEqualTo(earliestReviewTime);
    assertThat(response.startsAt()).isBeforeOrEqualTo(Instant.now());
    assertThat(response.expiresAt()).isEqualTo(
        response.startsAt().atZone(ZoneOffset.UTC).plusMonths(12).toInstant());
    assertThat(response.reviewedBy()).isEqualTo("admin@example.com");
    assertThat(response.rejectionReason()).isNull();

    var notification = ArgumentCaptor.forClass(UserNotification.class);
    verify(notifications).save(notification.capture());
    assertThat(notification.getValue().getUser()).isSameAs(user);
    assertThat(notification.getValue().getType()).isEqualTo("MEMBERSHIP_APPROVED");
    assertThat(notification.getValue().getLink()).isEqualTo("/dashboard/membership");
  }

  @Test
  void repeatedApprovalIsIdempotent() {
    var paid = membership(MembershipStatus.PAID, "CLIQ");
    var originalStart = Instant.parse("2026-01-15T10:00:00Z");
    paid.setPaidAt(originalStart);
    paid.setStartsAt(originalStart);
    paid.setExpiresAt(originalStart.atZone(ZoneOffset.UTC).plusMonths(12).toInstant());
    paid.setReviewedAt(originalStart);
    paid.setReviewedBy("first-admin@example.com");
    when(memberships.findById(91L)).thenReturn(Optional.of(paid));
    when(memberships.findByIdForUpdate(91L)).thenReturn(Optional.of(paid));

    var response = service.approve(91L, "second-admin@example.com");

    assertThat(response.startsAt()).isEqualTo(originalStart);
    assertThat(response.reviewedBy()).isEqualTo("first-admin@example.com");
    verifyNoInteractions(notifications);
  }

  @Test
  void approvalRejectsPendingRequestWhenUserAlreadyHasAnActiveMembership() {
    var pending = membership(MembershipStatus.PENDING, "CLIQ");
    var active = membership(MembershipStatus.PAID, "CLIQ");
    active.setId(92L);
    active.setStartsAt(Instant.now().minusSeconds(60));
    active.setExpiresAt(Instant.now().plusSeconds(3600));
    when(memberships.findById(91L)).thenReturn(Optional.of(pending));
    when(memberships.findByIdForUpdate(91L)).thenReturn(Optional.of(pending));
    when(memberships
        .findFirstByUserIdAndStatusAndStartsAtLessThanEqualAndExpiresAtAfterAndDeletedFalseOrderByExpiresAtDesc(
            eq(7L), eq(MembershipStatus.PAID), any(), any()))
        .thenReturn(Optional.of(active));

    assertThatThrownBy(() -> service.approve(91L, "admin@example.com"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("فعالة");

    assertThat(pending.getStatus()).isEqualTo(MembershipStatus.PENDING);
    verifyNoInteractions(notifications);
  }

  @Test
  void adminCanRejectCliqRequestWithOptionalNormalizedReason() {
    var pending = membership(MembershipStatus.PENDING, "CLIQ");
    when(memberships.findById(91L)).thenReturn(Optional.of(pending));
    when(memberships.findByIdForUpdate(91L)).thenReturn(Optional.of(pending));

    var response = service.reject(
        91L, new MembershipRejectionRequest("  تعذر مطابقة رقم الحوالة  "), "admin@example.com");

    assertThat(response.status()).isEqualTo(MembershipStatus.REJECTED);
    assertThat(response.active()).isFalse();
    assertThat(response.rejectionReason()).isEqualTo("تعذر مطابقة رقم الحوالة");
    assertThat(response.reviewedBy()).isEqualTo("admin@example.com");
    assertThat(response.reviewedAt()).isNotNull();
    assertThat(response.paidAt()).isNull();
    assertThat(response.startsAt()).isNull();
    assertThat(response.expiresAt()).isNull();
    verify(notifications).save(any(UserNotification.class));
  }

  @Test
  void adminReviewEndpointsRejectLegacyMockRequests() {
    var pending = membership(MembershipStatus.PENDING, "MOCK");
    when(memberships.findById(91L)).thenReturn(Optional.of(pending));
    when(memberships.findByIdForUpdate(91L)).thenReturn(Optional.of(pending));

    assertThatThrownBy(() -> service.approve(91L, "admin@example.com"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CliQ فقط");
    assertThatThrownBy(() -> service.reject(91L, null, "admin@example.com"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CliQ فقط");
    verifyNoInteractions(notifications);
  }

  @Test
  void disabledPlanCannotCreateCheckout() {
    when(plans.current()).thenReturn(plan(false));

    assertThatThrownBy(() -> service.checkout(user, checkoutRequest()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("غير متاح");
    verifyNoInteractions(memberships);
  }

  @Test
  void enabledLegacyPlanWithoutCliqAliasCannotAcceptManualCheckout() {
    when(plans.current()).thenReturn(new SubscriptionPlanService.PlanConfig(
        true, new BigDecimal("120.00"), "USD", 12, 4, Map.of(), ""));

    assertThatThrownBy(() -> service.checkout(user, checkoutRequest()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CliQ");
    verifyNoInteractions(memberships);
  }

  private AnnualMembership membership(MembershipStatus status, String provider) {
    var membership = new AnnualMembership();
    membership.setId(91L);
    membership.setUser(user);
    membership.setAmount(new BigDecimal("120.00"));
    membership.setCurrency("USD");
    membership.setStatus(status);
    membership.setPaymentProvider(provider);
    return membership;
  }

  private static MembershipCheckoutRequest checkoutRequest() {
    return new MembershipCheckoutRequest("Ahmad Student", "CLIQ-2026-001");
  }

  private static SubscriptionPlanService.PlanConfig plan(boolean enabled) {
    return new SubscriptionPlanService.PlanConfig(
        enabled, new BigDecimal("120.00"), "USD", 12, 4, Map.of(), "JAMEATI.CLIQ");
  }
}
