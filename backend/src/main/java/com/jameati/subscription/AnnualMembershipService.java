package com.jameati.subscription;

import static com.jameati.subscription.MembershipDtos.*;

import com.jameati.common.NotFoundException;
import com.jameati.me.UserNotification;
import com.jameati.me.UserNotificationRepository;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class AnnualMembershipService {
  private static final Duration CHECKOUT_QUOTE_TTL = Duration.ofMinutes(30);
  private static final String CLIQ_PROVIDER = "CLIQ";
  private static final String MOCK_PROVIDER = "MOCK";
  private static final String AUTO_RECEIPT_ACTOR = "AUTO_RECEIPT";
  private final AnnualMembershipRepository memberships;
  private final SubscriptionPlanService plans;
  private final UserRepository users;
  private final UserNotificationRepository notifications;
  private final MembershipReceiptStorageService receiptStorage;
  private final boolean receiptAutoActivationEnabled;

  public AnnualMembershipService(
      AnnualMembershipRepository memberships,
      SubscriptionPlanService plans,
      UserRepository users,
      UserNotificationRepository notifications,
      MembershipReceiptStorageService receiptStorage,
      @Value("${app.membership.receipt-auto-activation-enabled:true}")
          boolean receiptAutoActivationEnabled) {
    this.memberships = memberships;
    this.plans = plans;
    this.users = users;
    this.notifications = notifications;
    this.receiptStorage = receiptStorage;
    this.receiptAutoActivationEnabled = receiptAutoActivationEnabled;
  }

  public MembershipPlanResponse plan(
      ContentAccessService.AccessContext access,
      boolean testPaymentEnabled) {
    var plan = access.plan();
    return new MembershipPlanResponse(
        plan.enabled(), MembershipPlanCode.ANNUAL.price(), "JOD", plan.billingPeriodMonths(),
        plan.freeLimit(), plan.freeContent(), plan.cliqAlias(), testPaymentEnabled, access.fullAccess(),
        access.authenticated(), access.expiresAt(), java.util.Arrays.stream(MembershipPlanCode.values())
            .filter(MembershipPlanCode::purchasable)
            .map(code -> new MembershipPackageResponse(code, code.price(), "JOD", code.months(),
                code.contentAccess(), code.consultationAccess())).toList());
  }

  @Transactional
  public MembershipResponse checkout(User user, MembershipCheckoutRequest request) {
    var plan = plans.current();
    if (!plan.enabled()) throw new IllegalArgumentException("الاشتراكات غير متاحة حالياً");
    if (plan.cliqAlias().isBlank()) {
      throw new IllegalArgumentException("اسم CliQ المستعار غير متاح حالياً");
    }
    if (request == null) throw new IllegalArgumentException("بيانات حوالة CliQ مطلوبة");
    var code = MembershipPlanCode.orAnnual(request.planCode());
    code.requirePurchasable();
    var senderName = normalizeRequired(request.senderName(), 160, "اسم المحوّل");
    var paymentReference = normalizeRequired(request.paymentReference(), 100, "مرجع عملية CliQ");
    user = lockUser(user);

    var now = Instant.now();
    var active = activeForPlan(user, code, now);
    if (active.isPresent()) return response(active.get(), now);

    var previousPending = memberships
        .findFirstByUserIdAndPlanCodeAndStatusAndPaymentProviderAndDeletedFalseOrderByCreatedAtDesc(
            user.getId(), code, MembershipStatus.PENDING, CLIQ_PROVIDER);
    if (previousPending.isPresent()) {
      return response(previousPending.get(), now);
    }
    var pending = new AnnualMembership();
    pending.setUser(user);
    pending.setPlanCode(code);
    pending.setBillingPeriodMonths(code.months());
    pending.setAmount(code.price());
    pending.setCurrency("JOD");
    pending.setStatus(MembershipStatus.PENDING);
    pending.setPaymentProvider(CLIQ_PROVIDER);
    pending.setPaymentSenderName(senderName);
    pending.setPaymentReference(paymentReference);
    pending.setApplicantName(senderName);
    pending.setApplicantEmail(user.getEmail());
    pending.setPaidAt(null);
    pending.setStartsAt(null);
    pending.setExpiresAt(null);
    return response(memberships.save(pending), now);
  }

  @Transactional
  public MembershipResponse checkoutWithReceipt(
      User user,
      MembershipReceiptCheckoutRequest request,
      MultipartFile receiptImage) {
    var plan = plans.current();
    validateAvailableCliqPlan(plan);
    if (request == null) throw new IllegalArgumentException("بيانات طلب الاشتراك مطلوبة");
    var code = MembershipPlanCode.orAnnual(request.planCode());
    code.requirePurchasable();
    var applicantName = normalizeRequired(request.name(), 160, "الاسم");
    var applicantEmail = normalizeEmail(request.email());
    var paymentReference = normalizeOptionalReference(request.paymentReference());
    var lockedUser = lockUser(user);
    if (!applicantEmail.equalsIgnoreCase(lockedUser.getEmail())) {
      throw new IllegalArgumentException("البريد الإلكتروني يجب أن يطابق بريد الحساب المسجّل");
    }

    var now = Instant.now();
    var active = activeForPlan(lockedUser, code, now);
    if (active.isPresent()) return response(active.get(), now);

    var membership = memberships
        .findFirstByUserIdAndPlanCodeAndStatusAndPaymentProviderAndDeletedFalseOrderByCreatedAtDesc(
            lockedUser.getId(), code, MembershipStatus.PENDING, CLIQ_PROVIDER)
        .orElseGet(() -> newCliqMembership(lockedUser, code));
    var storedReceipt = receiptStorage.store(receiptImage);
    var duplicateReceipt = memberships.findFirstByReceiptSha256(storedReceipt.sha256());
    if (duplicateReceipt.isPresent()) {
      var duplicate = duplicateReceipt.get();
      if (membership.getId() == null || !membership.getId().equals(duplicate.getId())) {
        receiptStorage.discard(storedReceipt);
        throw new IllegalArgumentException("صورة الحوالة مستخدمة في طلب اشتراك آخر");
      }
      receiptStorage.discard(storedReceipt);
      storedReceipt = null;
    }
    membership.setPaymentSenderName(applicantName);
    membership.setApplicantName(applicantName);
    membership.setApplicantEmail(applicantEmail);
    if (paymentReference != null) membership.setPaymentReference(paymentReference);
    if (membership.getPaymentReference() == null || membership.getPaymentReference().isBlank()) {
      membership.setPaymentReference(
          "RECEIPT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT));
    }
    if (storedReceipt != null) {
      membership.setReceiptOriginalName(storedReceipt.originalName());
      membership.setReceiptStoredName(storedReceipt.storedName());
      membership.setReceiptContentType(storedReceipt.contentType());
      membership.setReceiptSizeBytes(storedReceipt.sizeBytes());
      membership.setReceiptSha256(storedReceipt.sha256());
      membership.setReceiptUploadedAt(now);
    }

    if (receiptAutoActivationEnabled && code != MembershipPlanCode.CONSULTATION) {
      activateFromReceipt(membership, now);
    } else {
      membership.setStatus(MembershipStatus.PENDING);
      membership.setAutoActivated(false);
      membership.setActivationSource(null);
      membership.setReviewedBy(null);
      membership.setReviewedAt(null);
      notifyUser(
          membership,
          "MEMBERSHIP_RECEIPT_RECEIVED",
          "تم استلام صورة الحوالة",
          "تم استلام طلب الاشتراك وصورة الحوالة، والطلب بانتظار المراجعة.");
    }
    return response(memberships.save(membership), now);
  }

  @Transactional
  public MembershipResponse mockPay(User user, Long id) {
    user = lockUser(user);
    var membership = requireMine(user, id);
    var now = Instant.now();
    if (!MOCK_PROVIDER.equals(membership.getPaymentProvider())) {
      throw new IllegalArgumentException("طلب CliQ يحتاج إلى مراجعة واعتماد من الإدارة");
    }
    if (membership.getStatus() == MembershipStatus.PAID) return response(membership, now);
    if (membership.getStatus() != MembershipStatus.PENDING) {
      throw new IllegalArgumentException("لا يمكن دفع الطلب في حالته الحالية");
    }
    if (!quoteIsFresh(membership, now)) {
      throw new IllegalArgumentException("انتهت صلاحية عرض السعر؛ ابدأ الاشتراك من جديد");
    }
    var alreadyActive = activeForPlan(user, membership.getPlanCode(), now);
    if (alreadyActive.isPresent()) return response(alreadyActive.get(), now);
    var plan = plans.current();
    if (!plan.enabled()) throw new IllegalArgumentException("الاشتراكات غير متاحة حالياً");

    membership.setStatus(MembershipStatus.PAID);
    membership.setPaymentReference("MOCK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
    membership.setPaidAt(now);
    membership.setStartsAt(now);
    membership.setExpiresAt(expiry(membership, now));
    membership.setAutoActivated(false);
    membership.setActivationSource("MOCK_PAYMENT");
    return response(membership, now);
  }

  @Transactional
  public MembershipResponse approve(Long id, String reviewer) {
    reviewer = normalizeReviewer(reviewer);
    var reviewTarget = lockCliqForReview(id);
    var membership = reviewTarget.membership();
    var now = Instant.now();
    if (membership.getStatus() == MembershipStatus.PAID) {
      return response(membership, now);
    }
    if (membership.getStatus() != MembershipStatus.PENDING) {
      throw new IllegalArgumentException("لا يمكن اعتماد طلب العضوية في حالته الحالية");
    }
    if (activeForPlan(reviewTarget.user(), membership.getPlanCode(), now).isPresent()) {
      throw new IllegalArgumentException("لدى المستخدم عضوية فعالة بالفعل؛ لا يمكن اعتماد طلب متداخل");
    }

    membership.setStatus(MembershipStatus.PAID);
    membership.setPaidAt(now);
    membership.setStartsAt(now);
    membership.setExpiresAt(
        expiry(membership, now));
    membership.setRejectionReason(null);
    membership.setReviewedBy(reviewer);
    membership.setReviewedAt(now);
    membership.setAutoActivated(false);
    membership.setActivationSource("ADMIN_REVIEW");
    notifyUser(
        membership,
        "MEMBERSHIP_APPROVED",
        "تم تفعيل باقتك",
        membership.getPlanCode() == MembershipPlanCode.CONSULTATION
            ? "تم تأكيد حوالة CliQ. يمكنك حجز الاستشارة ومتابعة مسارك حتى الحصول على المنحة أو الوظيفة."
            : "تم تأكيد حوالة CliQ وتفعيل الوصول إلى المحتوى لمدة " + membership.getBillingPeriodMonths() + " شهراً.");
    return response(membership, now);
  }

  @Transactional
  public MembershipResponse reject(Long id, MembershipRejectionRequest request, String reviewer) {
    reviewer = normalizeReviewer(reviewer);
    var membership = lockCliqForReview(id).membership();
    var now = Instant.now();
    if (membership.getStatus() == MembershipStatus.REJECTED) {
      return response(membership, now);
    }
    if (membership.getStatus() != MembershipStatus.PENDING) {
      throw new IllegalArgumentException("لا يمكن رفض طلب العضوية في حالته الحالية");
    }

    membership.setStatus(MembershipStatus.REJECTED);
    membership.setPaidAt(null);
    membership.setStartsAt(null);
    membership.setExpiresAt(null);
    membership.setRejectionReason(normalizeOptionalReason(request == null ? null : request.reason()));
    membership.setReviewedBy(reviewer);
    membership.setReviewedAt(now);
    notifyUser(
        membership,
        "MEMBERSHIP_REJECTED",
        "تعذر اعتماد طلب العضوية",
        "لم نتمكن من تأكيد حوالة CliQ. راجع طلبك ثم أرسل حوالة صحيحة عند الحاجة.");
    return response(membership, now);
  }

  public Optional<MembershipResponse> current(User user) {
    var now = Instant.now();
    return active(user, now).map(item -> response(item, now));
  }

  public Page<MembershipResponse> history(User user, Pageable pageable) {
    var now = Instant.now();
    return memberships.findByUserIdAndDeletedFalse(user.getId(), pageable)
        .map(item -> response(item, now));
  }

  public Page<MembershipResponse> adminList(MembershipStatus status, Pageable pageable) {
    var page = status == null
        ? memberships.findByDeletedFalse(pageable)
        : memberships.findByStatusAndDeletedFalse(status, pageable);
    var now = Instant.now();
    return page.map(item -> response(item, now));
  }

  public MembershipReceiptStorageService.StoredReceiptResource receiptForUser(
      User user, Long membershipId) {
    if (user == null || user.getId() == null) {
      throw new IllegalArgumentException("المستخدم غير صالح");
    }
    return receiptStorage.read(requireMine(user, membershipId));
  }

  public MembershipReceiptStorageService.StoredReceiptResource receiptForAdmin(Long membershipId) {
    var membership = memberships.findById(membershipId)
        .filter(item -> !item.isDeleted())
        .orElseThrow(() -> new NotFoundException("الاشتراك غير موجود"));
    return receiptStorage.read(membership);
  }

  private Optional<AnnualMembership> active(User user, Instant now) {
    return memberships
        .findFirstByUserIdAndStatusAndStartsAtLessThanEqualAndExpiresAtAfterAndDeletedFalseOrderByExpiresAtDesc(
            user.getId(), MembershipStatus.PAID, now, now);
  }

  private Optional<AnnualMembership> activeForPlan(User user, MembershipPlanCode code, Instant now) {
    if (code.contentAccess()) return active(user, now);
    return memberships.findByUserIdAndPlanCodeAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            user.getId(), MembershipPlanCode.CONSULTATION, MembershipStatus.PAID).stream()
        .filter(item -> item.getStartsAt() != null && !item.getStartsAt().isAfter(now))
        .filter(item -> !"ACHIEVED".equals(item.getConsultationProgress())).findFirst();
  }

  private static Instant expiry(AnnualMembership membership, Instant now) {
    return membership.getPlanCode() == MembershipPlanCode.CONSULTATION ? null
        : now.atZone(ZoneOffset.UTC).plusMonths(membership.getBillingPeriodMonths()).toInstant();
  }

  private static AnnualMembership newCliqMembership(
      User user, MembershipPlanCode code) {
    var membership = new AnnualMembership();
    membership.setUser(user);
    membership.setPlanCode(code);
    membership.setBillingPeriodMonths(code.months());
    membership.setAmount(code.price());
    membership.setCurrency("JOD");
    membership.setStatus(MembershipStatus.PENDING);
    membership.setPaymentProvider(CLIQ_PROVIDER);
    return membership;
  }

  private void activateFromReceipt(AnnualMembership membership, Instant now) {
    membership.setStatus(MembershipStatus.PAID);
    membership.setPaidAt(now);
    membership.setStartsAt(now);
    membership.setExpiresAt(
        expiry(membership, now));
    membership.setRejectionReason(null);
    membership.setReviewedBy(AUTO_RECEIPT_ACTOR);
    membership.setReviewedAt(now);
    membership.setAutoActivated(true);
    membership.setActivationSource(AUTO_RECEIPT_ACTOR);
    notifyUser(
        membership,
        "MEMBERSHIP_AUTO_ACTIVATED",
        "تم تفعيل باقتك تلقائياً",
        "تم استلام صورة الحوالة وتفعيل باقتك لمدة " + membership.getBillingPeriodMonths() + " شهراً.");
  }

  private static void validateAvailableCliqPlan(SubscriptionPlanService.PlanConfig plan) {
    if (!plan.enabled()) throw new IllegalArgumentException("الاشتراكات غير متاحة حالياً");
    if (plan.cliqAlias().isBlank()) {
      throw new IllegalArgumentException("اسم CliQ المستعار غير متاح حالياً");
    }
  }

  private AnnualMembership requireMine(User user, Long id) {
    return memberships.findByIdAndUserIdAndDeletedFalse(id, user.getId())
        .orElseThrow(() -> new NotFoundException("الاشتراك غير موجود"));
  }

  private ReviewTarget lockCliqForReview(Long id) {
    var snapshot = memberships.findById(id)
        .filter(item -> !item.isDeleted())
        .orElseThrow(() -> new NotFoundException("الاشتراك غير موجود"));
    if (!CLIQ_PROVIDER.equals(snapshot.getPaymentProvider())) {
      throw new IllegalArgumentException("يمكن للإدارة مراجعة طلبات CliQ فقط");
    }
    var user = lockUser(snapshot.getUser());
    var membership = memberships.findByIdForUpdate(id)
        .orElseThrow(() -> new NotFoundException("الاشتراك غير موجود"));
    if (!CLIQ_PROVIDER.equals(membership.getPaymentProvider())) {
      throw new IllegalArgumentException("يمكن للإدارة مراجعة طلبات CliQ فقط");
    }
    if (membership.getUser() == null
        || !user.getId().equals(membership.getUser().getId())) {
      throw new IllegalStateException("تغير صاحب طلب العضوية أثناء المراجعة");
    }
    return new ReviewTarget(user, membership);
  }

  private User lockUser(User user) {
    if (user == null || user.getId() == null) throw new IllegalArgumentException("المستخدم غير صالح");
    return users.findEnabledByIdForUpdate(user.getId())
        .orElseThrow(() -> new NotFoundException("المستخدم غير موجود"));
  }

  private static boolean quoteIsFresh(AnnualMembership membership, Instant now) {
    return membership.getStatus() == MembershipStatus.PENDING
        && membership.getCreatedAt() != null
        && membership.getCreatedAt().isAfter(now.minus(CHECKOUT_QUOTE_TTL));
  }

  private void notifyUser(AnnualMembership membership, String type, String title, String body) {
    var notification = new UserNotification();
    notification.setUser(membership.getUser());
    notification.setType(type);
    notification.setTitle(title);
    notification.setBody(body);
    notification.setLink("/dashboard/membership");
    notifications.save(notification);
  }

  private static String normalizeRequired(String value, int maxLength, String field) {
    if (value == null) throw new IllegalArgumentException(field + " مطلوب");
    var normalized = value.trim();
    if (normalized.isBlank() || normalized.length() > maxLength) {
      throw new IllegalArgumentException(field + " غير صالح");
    }
    return normalized;
  }

  private static String normalizeOptionalReason(String value) {
    if (value == null) return null;
    var normalized = value.trim();
    if (normalized.length() > 1000) throw new IllegalArgumentException("سبب الرفض أطول من المسموح");
    return normalized.isBlank() ? null : normalized;
  }

  private static String normalizeOptionalReference(String value) {
    if (value == null) return null;
    var normalized = value.trim();
    if (normalized.length() > 100) {
      throw new IllegalArgumentException("مرجع عملية CliQ غير صالح");
    }
    return normalized.isBlank() ? null : normalized;
  }

  private static String normalizeEmail(String value) {
    var normalized = normalizeRequired(value, 190, "البريد الإلكتروني")
        .toLowerCase(Locale.ROOT);
    if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
      throw new IllegalArgumentException("البريد الإلكتروني غير صالح");
    }
    return normalized;
  }

  private static String normalizeReviewer(String reviewer) {
    return normalizeRequired(reviewer, 190, "هوية المراجع");
  }

  private record ReviewTarget(User user, AnnualMembership membership) {}

  private static MembershipResponse response(AnnualMembership item, Instant now) {
    var user = item.getUser();
    var active = item.getStatus() == MembershipStatus.PAID
        && item.getStartsAt() != null && !item.getStartsAt().isAfter(now)
        && (item.getPlanCode() == MembershipPlanCode.CONSULTATION
            ? !"ACHIEVED".equals(item.getConsultationProgress())
            : item.getExpiresAt() != null && item.getExpiresAt().isAfter(now));
    return new MembershipResponse(
        item.getId(), user.getId(), user.getFullName(), user.getEmail(), item.getStatus(),
        item.getAmount(), item.getCurrency(), item.getBillingPeriodMonths(),
        item.getPaymentProvider(), item.getPaymentSenderName(), item.getPaymentReference(),
        item.getApplicantName(), item.getApplicantEmail(), item.getReceiptStoredName() != null,
        item.getReceiptStoredName() == null ? null : "/api/v1/me/memberships/" + item.getId() + "/receipt",
        item.getReceiptOriginalName(), item.getReceiptContentType(), item.getReceiptSizeBytes(),
        item.getReceiptUploadedAt(), item.isAutoActivated(), item.getActivationSource(), item.getPaidAt(),
        item.getStartsAt(), item.getExpiresAt(), item.getRejectionReason(), item.getReviewedBy(),
        item.getReviewedAt(), active, item.getCreatedAt(), item.getUpdatedAt(), item.getPlanCode(),
        active && item.getPlanCode() == MembershipPlanCode.CONSULTATION);
  }
}
