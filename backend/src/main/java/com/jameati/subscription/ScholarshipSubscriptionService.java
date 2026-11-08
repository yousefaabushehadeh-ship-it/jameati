package com.jameati.subscription;

import static com.jameati.subscription.ScholarshipSubscriptionDtos.*;

import com.jameati.common.NotFoundException;
import com.jameati.me.UserNotification;
import com.jameati.me.UserNotificationRepository;
import com.jameati.opportunity.OpportunityRepository;
import com.jameati.user.User;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ScholarshipSubscriptionService {
  private static final EnumSet<ScholarshipApplicationStatus> REVIEW_STATUSES = EnumSet.of(
      ScholarshipApplicationStatus.UNDER_REVIEW,
      ScholarshipApplicationStatus.NEEDS_CHANGES,
      ScholarshipApplicationStatus.APPROVED,
      ScholarshipApplicationStatus.REJECTED,
      ScholarshipApplicationStatus.CONTACTED);

  private final ScholarshipSubscriptionRepository subscriptions;
  private final OpportunityRepository opportunities;
  private final UserNotificationRepository notifications;
  private final ContentAccessService contentAccess;

  public ScholarshipSubscriptionService(
      ScholarshipSubscriptionRepository subscriptions,
      OpportunityRepository opportunities,
      UserNotificationRepository notifications,
      ContentAccessService contentAccess) {
    this.subscriptions = subscriptions;
    this.opportunities = opportunities;
    this.notifications = notifications;
    this.contentAccess = contentAccess;
  }

  @Transactional
  public SubscriptionResponse checkout(User user, Long opportunityId) {
    var opportunity = opportunities.findByIdAndDeletedFalse(opportunityId)
        .orElseThrow(() -> new NotFoundException("المنحة غير موجودة"));
    var viewer = contentAccess.resolve(user);
    if (contentAccess.isOpportunityLocked(viewer, opportunityId)) {
      throw new IllegalArgumentException("هذه المنحة تتطلب عضوية سنوية فعّالة");
    }
    var existing = subscriptions.findByUserIdAndOpportunityIdAndDeletedFalse(user.getId(), opportunityId);
    if (existing.isPresent()) return response(existing.get());
    if (!opportunity.isPublished() || !opportunity.isServiceEnabled() || opportunity.getServicePrice() == null) {
      throw new IllegalArgumentException("خدمة التقديم غير متاحة لهذه المنحة");
    }
    var subscription = new ScholarshipSubscription();
    subscription.setUser(user);
    subscription.setOpportunity(opportunity);
    subscription.setAmount(opportunity.getServicePrice());
    subscription.setCurrency(opportunity.getServiceCurrency());
    return response(subscriptions.save(subscription));
  }

  @Transactional
  public SubscriptionResponse mockPay(User user, Long id) {
    var subscription = requireMine(user, id);
    if (subscription.getPaymentStatus() == PaymentStatus.PAID) return response(subscription);
    if (subscription.getPaymentStatus() == PaymentStatus.REFUNDED) {
      throw new IllegalArgumentException("لا يمكن إعادة دفع طلب مسترد");
    }
    subscription.setPaymentStatus(PaymentStatus.PAID);
    subscription.setApplicationStatus(ScholarshipApplicationStatus.AWAITING_INFORMATION);
    subscription.setPaymentReference("MOCK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
    subscription.setPaidAt(Instant.now());
    notifyStudent(subscription, "PAYMENT_CONFIRMED", "تم تفعيل خدمة التقديم", "أكمل معلومات طلب المنحة ليبدأ فريق جامعتي المراجعة.");
    return response(subscription);
  }

  @Transactional
  public SubscriptionResponse submit(User user, Long id, ApplicationInformationRequest request) {
    var subscription = requireMine(user, id);
    if (subscription.getPaymentStatus() != PaymentStatus.PAID) {
      throw new IllegalArgumentException("يجب إتمام الدفع قبل إرسال المعلومات");
    }
    if (!EnumSet.of(ScholarshipApplicationStatus.AWAITING_INFORMATION, ScholarshipApplicationStatus.NEEDS_CHANGES)
        .contains(subscription.getApplicationStatus())) {
      throw new IllegalArgumentException("لا يمكن تعديل الطلب في حالته الحالية");
    }
    subscription.setFullName(request.fullName());
    subscription.setPhone(request.phone());
    subscription.setWhatsapp(request.whatsapp());
    subscription.setNationality(request.nationality());
    subscription.setResidenceCountry(request.residenceCountry());
    subscription.setDateOfBirth(request.dateOfBirth());
    subscription.setDegreeLevel(request.degreeLevel());
    subscription.setUniversity(request.university());
    subscription.setSpeciality(request.speciality());
    subscription.setGraduationYear(request.graduationYear());
    subscription.setGpa(request.gpa());
    subscription.setEnglishLevel(request.englishLevel());
    subscription.setNotes(request.notes());
    subscription.setDocumentsText(request.documentsText());
    subscription.setApplicationStatus(ScholarshipApplicationStatus.SUBMITTED);
    subscription.setSubmittedAt(Instant.now());
    notifyStudent(subscription, "APPLICATION_SUBMITTED", "استلمنا معلوماتك", "سيبدأ فريق جامعتي مراجعة طلبك وسنتواصل معك عند الحاجة.");
    return response(subscription);
  }

  public Page<SubscriptionResponse> mine(User user, Pageable pageable) {
    return subscriptions.findByUserIdAndDeletedFalse(user.getId(), pageable).map(this::response);
  }

  public SubscriptionResponse mineDetail(User user, Long id) { return response(requireMine(user, id)); }

  public Page<SubscriptionResponse> adminList(ScholarshipApplicationStatus status, Pageable pageable) {
    var page = status == null
        ? subscriptions.findByDeletedFalse(pageable)
        : subscriptions.findByApplicationStatusAndDeletedFalse(status, pageable);
    return page.map(this::response);
  }

  public SubscriptionResponse adminDetail(Long id) { return response(require(id)); }

  @Transactional
  public SubscriptionResponse review(Long id, AdminReviewRequest request, String reviewer) {
    if (!REVIEW_STATUSES.contains(request.status())) {
      throw new IllegalArgumentException("حالة المراجعة غير صالحة");
    }
    var subscription = require(id);
    if (subscription.getPaymentStatus() != PaymentStatus.PAID || subscription.getSubmittedAt() == null) {
      throw new IllegalArgumentException("لا يمكن مراجعة طلب لم يُدفع ويُرسل بعد");
    }
    subscription.setApplicationStatus(request.status());
    subscription.setReviewNotes(request.reviewNotes());
    subscription.setReviewedBy(reviewer);
    subscription.setReviewedAt(Instant.now());
    if (request.status() == ScholarshipApplicationStatus.CONTACTED) subscription.setContactedAt(Instant.now());
    notifyStudent(subscription, "APPLICATION_STATUS", "تحديث على طلب المنحة", statusMessage(request.status()));
    return response(subscription);
  }

  private ScholarshipSubscription requireMine(User user, Long id) {
    return subscriptions.findByIdAndUserIdAndDeletedFalse(id, user.getId())
        .orElseThrow(() -> new NotFoundException("طلب الخدمة غير موجود"));
  }

  private ScholarshipSubscription require(Long id) {
    return subscriptions.findById(id).filter(item -> !item.isDeleted())
        .orElseThrow(() -> new NotFoundException("طلب الخدمة غير موجود"));
  }

  private void notifyStudent(ScholarshipSubscription subscription, String type, String title, String body) {
    var notification = new UserNotification();
    notification.setUser(subscription.getUser());
    notification.setType(type);
    notification.setTitle(title);
    notification.setBody(body);
    notification.setLink("/dashboard/subscriptions/" + subscription.getId());
    notifications.save(notification);
  }

  private static String statusMessage(ScholarshipApplicationStatus status) {
    return switch (status) {
      case UNDER_REVIEW -> "بدأ الفريق مراجعة معلوماتك ووثائقك.";
      case NEEDS_CHANGES -> "نحتاج منك تعديل بعض المعلومات. افتح الطلب للاطلاع على ملاحظات الفريق.";
      case APPROVED -> "اكتملت مراجعة طلبك بنجاح، وسيتابع الفريق معك خطوات التقديم.";
      case REJECTED -> "اكتملت المراجعة، وتوجد ملاحظات مهمة داخل صفحة الطلب.";
      case CONTACTED -> "تم التواصل معك من فريق جامعتي بخصوص طلبك.";
      default -> "تم تحديث حالة طلبك.";
    };
  }

  private SubscriptionResponse response(ScholarshipSubscription item) {
    var opportunity = item.getOpportunity();
    var user = item.getUser();
    return new SubscriptionResponse(
        item.getId(), opportunity.getId(), opportunity.getSlug(), opportunity.getTitle(), opportunity.getOrganization(),
        user.getFullName(), user.getEmail(), item.getAmount(), item.getCurrency(), item.getPaymentStatus(),
        item.getApplicationStatus(), item.getPaymentProvider(), item.getPaymentReference(), item.getPaidAt(),
        item.getSubmittedAt(), item.getReviewedAt(), item.getContactedAt(), item.getFullName(), item.getPhone(),
        item.getWhatsapp(), item.getNationality(), item.getResidenceCountry(), item.getDateOfBirth(),
        item.getDegreeLevel(), item.getUniversity(), item.getSpeciality(), item.getGraduationYear(), item.getGpa(),
        item.getEnglishLevel(), item.getNotes(), item.getDocumentsText(), item.getReviewNotes(), item.getReviewedBy(),
        item.getCreatedAt(), item.getUpdatedAt());
  }
}
