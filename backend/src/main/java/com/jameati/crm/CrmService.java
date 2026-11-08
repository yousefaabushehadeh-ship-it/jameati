package com.jameati.crm;

import static com.jameati.crm.CrmDtos.*;

import com.jameati.common.NotFoundException;
import com.jameati.user.RoleName;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true)
public class CrmService {
  private static final Set<RoleName> OWNER_ROLES = EnumSet.of(
      RoleName.SALES,
      RoleName.OPERATIONS,
      RoleName.SECRETARY,
      RoleName.ADMIN,
      RoleName.SUPER_ADMIN);
  private static final Set<CrmActivityType> MANUAL_ACTIVITY_TYPES = EnumSet.of(
      CrmActivityType.NOTE,
      CrmActivityType.CALL,
      CrmActivityType.WHATSAPP,
      CrmActivityType.EMAIL,
      CrmActivityType.MEETING);

  private final StudentLeadRepository leads;
  private final B2bAccountRepository accounts;
  private final CrmFollowUpRepository followUps;
  private final CrmActivityRepository activities;
  private final UserRepository users;
  private final ZoneId timezone;
  private final Duration initialFollowUpDelay;

  public CrmService(
      StudentLeadRepository leads,
      B2bAccountRepository accounts,
      CrmFollowUpRepository followUps,
      CrmActivityRepository activities,
      UserRepository users,
      @Value("${app.crm.timezone:Asia/Amman}") String timezone,
      @Value("${app.crm.initial-follow-up-hours:24}") long initialFollowUpHours) {
    this.leads = leads;
    this.accounts = accounts;
    this.followUps = followUps;
    this.activities = activities;
    this.users = users;
    this.timezone = ZoneId.of(timezone);
    this.initialFollowUpDelay = Duration.ofHours(Math.max(1, initialFollowUpHours));
  }

  public Page<StudentLeadResponse> leads(
      String query,
      LeadStatus status,
      String source,
      Long ownerId,
      CrmPaymentStatus paymentStatus,
      FollowUpBucket followUpBucket,
      Pageable pageable) {
    return leads.findAll(
        CrmSpecifications.leads(query, status, source, ownerId, paymentStatus, followUpBucket, timezone),
        pageable).map(this::leadResponse);
  }

  public StudentLeadDetail lead(Long id) {
    var lead = requireLead(id);
    return new StudentLeadDetail(
        leadResponse(lead),
        activities.findByStudentLeadIdAndDeletedFalseOrderByOccurredAtDesc(id).stream()
            .map(this::activityResponse).toList());
  }

  @Transactional
  public StudentLeadResponse createLead(StudentLeadRequest request, String actorEmail) {
    var email = request.email().trim().toLowerCase(Locale.ROOT);
    var phone = normalizePhone(request.phone());
    validateUniqueLeadContact(email, phone, null);
    var lead = new StudentLead();
    applyLead(lead, request);
    validateOperationalLead(lead);
    lead = leads.saveAndFlush(lead);
    syncLeadFollowUp(lead);
    activity(lead, null, CrmActivityType.CREATED, "تم إنشاء Lead يدويًا", actor(actorEmail), Instant.now());
    if (lead.getSummaryNotes() != null) {
      activity(lead, null, CrmActivityType.NOTE, lead.getSummaryNotes(), actor(actorEmail), Instant.now());
    }
    return leadResponse(lead);
  }

  @Transactional
  public StudentLeadResponse updateLead(Long id, StudentLeadRequest request, String actorEmail) {
    var lead = leads.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("الـLead غير موجود"));
    validateUniqueLeadContact(
        request.email().trim().toLowerCase(Locale.ROOT), normalizePhone(request.phone()), id);
    var previousStatus = lead.getStatus();
    var previousOwner = lead.getOwner();
    var previousPayment = lead.getPaymentStatus();
    var previousSummary = lead.getSummaryNotes();
    applyLead(lead, request);
    validateOperationalLead(lead);
    syncLeadFollowUp(lead);
    var actor = actor(actorEmail);
    if (previousStatus != lead.getStatus()) {
      activity(lead, null, CrmActivityType.STATUS_CHANGED,
          "تغيير الحالة من " + previousStatus + " إلى " + lead.getStatus(), actor, Instant.now());
    }
    if (!Objects.equals(id(previousOwner), id(lead.getOwner()))) {
      activity(lead, null, CrmActivityType.OWNER_CHANGED,
          "تغيير المسؤول إلى " + lead.getOwner().getFullName(), actor, Instant.now());
    }
    if (previousPayment != lead.getPaymentStatus()) {
      activity(lead, null, CrmActivityType.PAYMENT_UPDATED,
          "تغيير حالة الدفع من " + previousPayment + " إلى " + lead.getPaymentStatus(), actor, Instant.now());
    }
    if (!Objects.equals(previousSummary, lead.getSummaryNotes()) && lead.getSummaryNotes() != null) {
      activity(lead, null, CrmActivityType.NOTE, lead.getSummaryNotes(), actor, Instant.now());
    }
    return leadResponse(lead);
  }

  @Transactional
  public ActivityResponse addLeadActivity(Long id, ActivityRequest request, String actorEmail) {
    validateManualActivity(request.type());
    var lead = leads.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("الـLead غير موجود"));
    var when = request.occurredAt() == null ? Instant.now() : request.occurredAt();
    if (contactActivity(request.type())) lead.setLastContactAt(when);
    return activityResponse(activity(lead, null, request.type(), request.details(), actor(actorEmail), when));
  }

  public Page<B2bAccountResponse> accounts(
      String query,
      B2bStage stage,
      OrganizationType type,
      Long ownerId,
      FollowUpBucket followUpBucket,
      Pageable pageable) {
    return accounts.findAll(
        CrmSpecifications.accounts(query, stage, type, ownerId, followUpBucket, timezone), pageable)
        .map(this::accountResponse);
  }

  public B2bAccountDetail account(Long id) {
    var account = requireAccount(id);
    return new B2bAccountDetail(
        accountResponse(account),
        activities.findByB2bAccountIdAndDeletedFalseOrderByOccurredAtDesc(id).stream()
            .map(this::activityResponse).toList());
  }

  @Transactional
  public B2bAccountResponse createAccount(B2bAccountRequest request, String actorEmail) {
    var account = new B2bAccount();
    applyAccount(account, request);
    validateOperationalAccount(account);
    account = accounts.saveAndFlush(account);
    syncAccountFollowUp(account);
    activity(null, account, CrmActivityType.CREATED, "تم إنشاء حساب B2B", actor(actorEmail), Instant.now());
    if (account.getSummaryNotes() != null) {
      activity(null, account, CrmActivityType.NOTE, account.getSummaryNotes(), actor(actorEmail), Instant.now());
    }
    return accountResponse(account);
  }

  @Transactional
  public B2bAccountResponse updateAccount(Long id, B2bAccountRequest request, String actorEmail) {
    var account = accounts.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("حساب B2B غير موجود"));
    var previousStage = account.getStage();
    var previousOwner = account.getOwner();
    var previousSummary = account.getSummaryNotes();
    applyAccount(account, request);
    validateOperationalAccount(account);
    syncAccountFollowUp(account);
    var actor = actor(actorEmail);
    if (previousStage != account.getStage()) {
      activity(null, account, CrmActivityType.STATUS_CHANGED,
          "تغيير المرحلة من " + previousStage + " إلى " + account.getStage(), actor, Instant.now());
    }
    if (!Objects.equals(id(previousOwner), id(account.getOwner()))) {
      activity(null, account, CrmActivityType.OWNER_CHANGED,
          "تغيير المسؤول إلى " + account.getOwner().getFullName(), actor, Instant.now());
    }
    if (!Objects.equals(previousSummary, account.getSummaryNotes()) && account.getSummaryNotes() != null) {
      activity(null, account, CrmActivityType.NOTE, account.getSummaryNotes(), actor, Instant.now());
    }
    return accountResponse(account);
  }

  @Transactional
  public ActivityResponse addAccountActivity(Long id, ActivityRequest request, String actorEmail) {
    validateManualActivity(request.type());
    var account = accounts.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("حساب B2B غير موجود"));
    var when = request.occurredAt() == null ? Instant.now() : request.occurredAt();
    if (contactActivity(request.type())) account.setLastContactAt(when);
    return activityResponse(activity(null, account, request.type(), request.details(), actor(actorEmail), when));
  }

  public Page<FollowUpResponse> followUps(
      FollowUpBucket bucket, Long ownerId, CrmTargetType targetType, Pageable pageable) {
    return followUps.findAll(CrmSpecifications.followUps(bucket, ownerId, targetType, timezone), pageable)
        .map(this::followUpResponse);
  }

  @Transactional(isolation = Isolation.SERIALIZABLE)
  public FollowUpResponse completeFollowUp(Long id, CompleteFollowUpRequest request, String actorEmail) {
    var followUp = followUps.findByIdForUpdate(id)
        .orElseThrow(() -> new NotFoundException("المتابعة غير موجودة"));
    if (followUp.getStatus() != FollowUpStatus.PENDING) {
      throw new IllegalArgumentException("تم إغلاق هذه المتابعة مسبقًا");
    }
    var now = Instant.now();
    var actor = actor(actorEmail);
    followUp.setStatus(FollowUpStatus.DONE);
    followUp.setResult(clean(request.result()));
    followUp.setCompletedAt(now);
    followUp.setCompletedBy(actor);
    if (followUp.getStudentLead() != null) {
      var lead = leads.findByIdForUpdate(followUp.getStudentLead().getId()).orElseThrow();
      lead.setLastContactAt(now);
      scheduleNext(lead, request.nextFollowUpAt(), request.nextAction(), actor);
      activity(lead, null, CrmActivityType.FOLLOW_UP_COMPLETED, request.result(), actor, now);
    } else {
      var account = accounts.findByIdForUpdate(followUp.getB2bAccount().getId()).orElseThrow();
      account.setLastContactAt(now);
      scheduleNext(account, request.nextFollowUpAt(), request.nextAction(), actor);
      activity(null, account, CrmActivityType.FOLLOW_UP_COMPLETED, request.result(), actor, now);
    }
    return followUpResponse(followUp);
  }

  @Transactional
  public FollowUpResponse rescheduleFollowUp(Long id, RescheduleFollowUpRequest request, String actorEmail) {
    var followUp = followUps.findByIdForUpdate(id)
        .orElseThrow(() -> new NotFoundException("المتابعة غير موجودة"));
    if (followUp.getStatus() != FollowUpStatus.PENDING) {
      throw new IllegalArgumentException("لا يمكن تأجيل متابعة مغلقة");
    }
    followUp.setDueAt(request.dueAt());
    followUp.setAction(request.action().trim());
    var actor = actor(actorEmail);
    if (followUp.getStudentLead() != null) {
      var lead = leads.findByIdForUpdate(followUp.getStudentLead().getId()).orElseThrow();
      lead.setNextFollowUpAt(request.dueAt());
      lead.setNextAction(request.action().trim());
      activity(lead, null, CrmActivityType.FOLLOW_UP_RESCHEDULED,
          "تأجيل المتابعة إلى " + request.dueAt(), actor, Instant.now());
    } else {
      var account = accounts.findByIdForUpdate(followUp.getB2bAccount().getId()).orElseThrow();
      account.setNextFollowUpAt(request.dueAt());
      account.setNextAction(request.action().trim());
      activity(null, account, CrmActivityType.FOLLOW_UP_RESCHEDULED,
          "تأجيل المتابعة إلى " + request.dueAt(), actor, Instant.now());
    }
    return followUpResponse(followUp);
  }

  public List<OwnerResponse> owners() {
    return users.findAll(Sort.by(Sort.Direction.ASC, "fullName")).stream()
        .filter(user -> !user.isDeleted() && user.isEnabled())
        .filter(user -> user.getRoles().stream().anyMatch(OWNER_ROLES::contains))
        .map(this::ownerResponse)
        .toList();
  }

  public DashboardResponse dashboard() {
    var day = LocalDate.now(timezone);
    var start = day.atStartOfDay(timezone).toInstant();
    var end = day.plusDays(1).atStartOfDay(timezone).toInstant();
    var allLeadSpec = CrmSpecifications.leads("", null, null, null, null, null, timezone);
    var allAccountSpec = CrmSpecifications.accounts("", null, null, null, null, timezone);
    var newLeads = allLeadSpec.and((root, ignored, cb) -> cb.and(
        cb.greaterThanOrEqualTo(root.get("createdAt"), start),
        cb.lessThan(root.get("createdAt"), end)));
    var unassigned = allLeadSpec.and((root, ignored, cb) -> cb.isNull(root.get("owner")));
    var paid = allLeadSpec.and((root, ignored, cb) -> cb.equal(root.get("paymentStatus"), CrmPaymentStatus.PAID));
    var newAccounts = allAccountSpec.and((root, ignored, cb) -> cb.and(
        cb.greaterThanOrEqualTo(root.get("createdAt"), start),
        cb.lessThan(root.get("createdAt"), end)));
    var studentPipeline = Arrays.stream(LeadStatus.values())
        .map(status -> new PipelineCount(status.name(), leads.count(allLeadSpec.and(
            (root, ignored, cb) -> cb.equal(root.get("status"), status)))))
        .toList();
    var b2bPipeline = Arrays.stream(B2bStage.values())
        .map(stage -> new PipelineCount(stage.name(), accounts.count(allAccountSpec.and(
            (root, ignored, cb) -> cb.equal(root.get("stage"), stage)))))
        .toList();
    var activityToday = activities.countsBetween(start, end).stream().collect(Collectors.toMap(
        row -> ((CrmActivityType) row[0]).name(), row -> (Long) row[1]));
    return new DashboardResponse(
        leads.count(allLeadSpec),
        leads.count(newLeads),
        leads.count(unassigned),
        followUps.countPendingBetween(FollowUpStatus.PENDING, start, end),
        followUps.countByDeletedFalseAndStatusAndDueAtBefore(FollowUpStatus.PENDING, start),
        leads.count(paid),
        Optional.ofNullable(leads.paidRevenueJod()).orElse(BigDecimal.ZERO),
        accounts.count(allAccountSpec),
        accounts.count(newAccounts),
        studentPipeline,
        b2bPipeline,
        activityToday);
  }

  @Transactional
  public StudentLeadResponse ensureLeadForRegistration(User user, String phone) {
    var existing = leads.findByUserIdAndDeletedFalse(user.getId()).orElse(null);
    if (existing != null) return leadResponse(existing);
    var normalizedPhone = normalizePhone(phone);
    existing = leads.findFirstByEmailIgnoreCaseAndUserIsNullAndDeletedFalse(user.getEmail()).orElse(null);
    if (existing == null) {
      var phoneMatches = leads.findByPhoneAndUserIsNullAndDeletedFalse(normalizedPhone);
      if (phoneMatches.size() == 1) existing = phoneMatches.getFirst();
    }
    var lead = existing == null ? new StudentLead() : existing;
    lead.setUser(user);
    lead.setFullName(user.getFullName());
    lead.setEmail(user.getEmail());
    lead.setPhone(normalizedPhone);
    if (existing == null || lead.getSource() == null || lead.getSource().isBlank()) {
      lead.setSource("Website");
    }
    if (lead.getStatus() == null) lead.setStatus(LeadStatus.NEW);
    if (lead.getPaymentStatus() == null) lead.setPaymentStatus(CrmPaymentStatus.NOT_APPLICABLE);
    if (lead.getNextFollowUpAt() == null && !lead.getStatus().terminal()) {
      lead.setNextFollowUpAt(Instant.now().plus(initialFollowUpDelay));
      lead.setNextAction("تواصل أولي مع الطالب الجديد");
    }
    lead = leads.saveAndFlush(lead);
    if (followUps.findByStudentLeadIdAndStatusAndDeletedFalseOrderByDueAtAsc(
        lead.getId(), FollowUpStatus.PENDING).isEmpty() && !lead.getStatus().terminal()) {
      createFollowUp(lead, null, lead.getOwner(), lead.getNextFollowUpAt(), lead.getNextAction());
    }
    activity(lead, null, existing == null ? CrmActivityType.CREATED : CrmActivityType.SYSTEM,
        existing == null ? "تم إنشاء Lead تلقائيًا من التسجيل" : "تم ربط الحساب بـLead موجود",
        null, Instant.now());
    return leadResponse(lead);
  }

  public String exportLeadsCsv(
      String query,
      LeadStatus status,
      String source,
      Long ownerId,
      CrmPaymentStatus paymentStatus,
      FollowUpBucket followUpBucket) {
    var rows = leads.findAll(
        CrmSpecifications.leads(query, status, source, ownerId, paymentStatus, followUpBucket, timezone),
        Sort.by(Sort.Direction.DESC, "createdAt"));
    var output = new StringBuilder("\uFEFFLead ID,Date Added,Full Name,Phone,Email,Age,City,Education Level,Major,English Level,Main Need,Interest,Source,Owner,Status,Last Contact,Next Follow-up,Next Action,Service / Package,Amount (JOD),Payment Status,Notes\n");
    rows.forEach(lead -> {
      var response = leadResponse(lead);
      appendCsv(output,
          response.code(), response.createdAt(), response.fullName(), response.phone(), response.email(), response.age(),
          response.city(), response.educationLevel(), response.major(), response.englishLevel(), response.mainNeed(),
          response.interest(), response.source(), response.owner() == null ? null : response.owner().fullName(),
          response.status(), response.lastContactAt(), response.nextFollowUpAt(), response.nextAction(),
          response.servicePackage(), response.amountJod(), response.paymentStatus(), response.summaryNotes());
    });
    return output.toString();
  }

  private void applyLead(StudentLead lead, StudentLeadRequest request) {
    lead.setFullName(request.fullName().trim());
    lead.setPhone(normalizePhone(request.phone()));
    lead.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
    lead.setDateOfBirth(request.dateOfBirth());
    lead.setCity(clean(request.city()));
    lead.setEducationLevel(clean(request.educationLevel()));
    lead.setMajor(clean(request.major()));
    lead.setEnglishLevel(clean(request.englishLevel()));
    lead.setMainNeed(clean(request.mainNeed()));
    lead.setInterest(clean(request.interest()));
    lead.setSource(request.source().trim());
    lead.setOwner(owner(request.ownerId()));
    lead.setStatus(request.status());
    lead.setLastContactAt(request.lastContactAt());
    lead.setNextFollowUpAt(request.nextFollowUpAt());
    lead.setNextAction(clean(request.nextAction()));
    lead.setServicePackage(clean(request.servicePackage()));
    lead.setAmountJod(request.amountJod());
    lead.setPaymentStatus(request.paymentStatus());
    lead.setSummaryNotes(clean(request.summaryNotes()));
  }

  private void applyAccount(B2bAccount account, B2bAccountRequest request) {
    account.setOrganization(request.organization().trim());
    account.setType(request.type());
    account.setContactPerson(request.contactPerson().trim());
    account.setJobTitle(clean(request.jobTitle()));
    account.setPhone(normalizePhone(request.phone()));
    account.setEmail(cleanLower(request.email()));
    account.setCity(clean(request.city()));
    account.setStudentCount(request.studentCount());
    account.setSource(request.source().trim());
    account.setOwner(owner(request.ownerId()));
    account.setStage(request.stage());
    account.setLastContactAt(request.lastContactAt());
    account.setNextFollowUpAt(request.nextFollowUpAt());
    account.setNextAction(clean(request.nextAction()));
    account.setOffer(clean(request.offer()));
    account.setEstimatedValueJod(request.estimatedValueJod());
    account.setProposalSentAt(request.proposalSentAt());
    account.setProbability(request.probability());
    account.setSummaryNotes(clean(request.summaryNotes()));
  }

  private void validateOperationalLead(StudentLead lead) {
    if (lead.getDateOfBirth() != null && lead.getDateOfBirth().isAfter(LocalDate.now(timezone))) {
      throw new IllegalArgumentException("تاريخ الميلاد لا يمكن أن يكون في المستقبل");
    }
    if (!lead.getStatus().terminal()) {
      requireOperationalFields(lead.getOwner(), lead.getNextFollowUpAt(), lead.getNextAction());
    }
  }

  private void validateOperationalAccount(B2bAccount account) {
    if (!account.getStage().terminal()) {
      requireOperationalFields(account.getOwner(), account.getNextFollowUpAt(), account.getNextAction());
    }
  }

  private void requireOperationalFields(User owner, Instant nextFollowUpAt, String nextAction) {
    if (owner == null) throw new IllegalArgumentException("يجب تعيين مسؤول للسجل");
    if (nextFollowUpAt == null) throw new IllegalArgumentException("يجب تحديد موعد المتابعة القادمة");
    if (nextAction == null || nextAction.isBlank()) throw new IllegalArgumentException("يجب تحديد الإجراء القادم");
  }

  private void validateUniqueLeadContact(String email, String phone, Long ignoredLeadId) {
    var emailConflict = leads.findFirstByEmailIgnoreCaseAndDeletedFalse(email)
        .filter(match -> !Objects.equals(match.getId(), ignoredLeadId))
        .isPresent();
    var phoneConflict = leads.findByPhoneAndDeletedFalse(phone).stream()
        .anyMatch(match -> !Objects.equals(match.getId(), ignoredLeadId));
    if (emailConflict || phoneConflict) {
      throw new IllegalArgumentException("يوجد Lead بنفس البريد أو رقم الهاتف");
    }
  }

  private void syncLeadFollowUp(StudentLead lead) {
    var pending = followUps.findByStudentLeadIdAndStatusAndDeletedFalseOrderByDueAtAsc(
        lead.getId(), FollowUpStatus.PENDING);
    if (lead.getStatus().terminal()) {
      pending.forEach(item -> item.setStatus(FollowUpStatus.CANCELLED));
      lead.setNextFollowUpAt(null);
      lead.setNextAction(null);
      return;
    }
    var current = pending.isEmpty() ? new CrmFollowUp() : pending.getFirst();
    current.setStudentLead(lead);
    current.setB2bAccount(null);
    current.setOwner(lead.getOwner());
    current.setDueAt(lead.getNextFollowUpAt());
    current.setAction(lead.getNextAction());
    current.setStatus(FollowUpStatus.PENDING);
    followUps.save(current);
    pending.stream().skip(1).forEach(item -> item.setStatus(FollowUpStatus.CANCELLED));
  }

  private void syncAccountFollowUp(B2bAccount account) {
    var pending = followUps.findByB2bAccountIdAndStatusAndDeletedFalseOrderByDueAtAsc(
        account.getId(), FollowUpStatus.PENDING);
    if (account.getStage().terminal()) {
      pending.forEach(item -> item.setStatus(FollowUpStatus.CANCELLED));
      account.setNextFollowUpAt(null);
      account.setNextAction(null);
      return;
    }
    var current = pending.isEmpty() ? new CrmFollowUp() : pending.getFirst();
    current.setStudentLead(null);
    current.setB2bAccount(account);
    current.setOwner(account.getOwner());
    current.setDueAt(account.getNextFollowUpAt());
    current.setAction(account.getNextAction());
    current.setStatus(FollowUpStatus.PENDING);
    followUps.save(current);
    pending.stream().skip(1).forEach(item -> item.setStatus(FollowUpStatus.CANCELLED));
  }

  private void scheduleNext(StudentLead lead, Instant dueAt, String nextAction, User actor) {
    if (lead.getStatus().terminal()) {
      lead.setNextFollowUpAt(null);
      lead.setNextAction(null);
      return;
    }
    if (dueAt == null || clean(nextAction) == null) {
      throw new IllegalArgumentException("أكمل المتابعة بتحديد الموعد والإجراء القادمين");
    }
    lead.setNextFollowUpAt(dueAt);
    lead.setNextAction(clean(nextAction));
    createFollowUp(lead, null, lead.getOwner(), dueAt, clean(nextAction));
    activity(lead, null, CrmActivityType.FOLLOW_UP_CREATED,
        "المتابعة القادمة: " + clean(nextAction), actor, Instant.now());
  }

  private void scheduleNext(B2bAccount account, Instant dueAt, String nextAction, User actor) {
    if (account.getStage().terminal()) {
      account.setNextFollowUpAt(null);
      account.setNextAction(null);
      return;
    }
    if (dueAt == null || clean(nextAction) == null) {
      throw new IllegalArgumentException("أكمل المتابعة بتحديد الموعد والإجراء القادمين");
    }
    account.setNextFollowUpAt(dueAt);
    account.setNextAction(clean(nextAction));
    createFollowUp(null, account, account.getOwner(), dueAt, clean(nextAction));
    activity(null, account, CrmActivityType.FOLLOW_UP_CREATED,
        "المتابعة القادمة: " + clean(nextAction), actor, Instant.now());
  }

  private CrmFollowUp createFollowUp(
      StudentLead lead, B2bAccount account, User owner, Instant dueAt, String action) {
    var followUp = new CrmFollowUp();
    followUp.setStudentLead(lead);
    followUp.setB2bAccount(account);
    followUp.setOwner(owner);
    followUp.setDueAt(dueAt);
    followUp.setAction(action);
    return followUps.save(followUp);
  }

  private CrmActivity activity(
      StudentLead lead,
      B2bAccount account,
      CrmActivityType type,
      String details,
      User actor,
      Instant occurredAt) {
    var activity = new CrmActivity();
    activity.setStudentLead(lead);
    activity.setB2bAccount(account);
    activity.setActor(actor);
    activity.setType(type);
    activity.setDetails(details.trim());
    activity.setOccurredAt(occurredAt);
    return activities.save(activity);
  }

  private StudentLead requireLead(Long id) {
    return leads.findByIdAndDeletedFalse(id).orElseThrow(() -> new NotFoundException("الـLead غير موجود"));
  }

  private B2bAccount requireAccount(Long id) {
    return accounts.findByIdAndDeletedFalse(id).orElseThrow(() -> new NotFoundException("حساب B2B غير موجود"));
  }

  private User owner(Long id) {
    var owner = users.findByIdAndDeletedFalse(id)
        .filter(User::isEnabled)
        .orElseThrow(() -> new IllegalArgumentException("المسؤول المختار غير متاح"));
    if (owner.getRoles().stream().noneMatch(OWNER_ROLES::contains)) {
      throw new IllegalArgumentException("الحساب المختار لا يملك صلاحية CRM");
    }
    return owner;
  }

  private User actor(String email) {
    if (email == null || email.isBlank()) return null;
    return users.findByEmailIgnoreCaseAndDeletedFalse(email).orElse(null);
  }

  private StudentLeadResponse leadResponse(StudentLead lead) {
    Integer age = lead.getDateOfBirth() == null ? null
        : Period.between(lead.getDateOfBirth(), LocalDate.now(timezone)).getYears();
    return new StudentLeadResponse(
        lead.getId(), leadCode(lead.getId()), id(lead.getUser()), lead.getFullName(), lead.getPhone(),
        lead.getEmail(), lead.getDateOfBirth(), age, lead.getCity(), lead.getEducationLevel(), lead.getMajor(),
        lead.getEnglishLevel(), lead.getMainNeed(), lead.getInterest(), lead.getSource(),
        ownerResponse(lead.getOwner()), lead.getStatus(), lead.getLastContactAt(), lead.getNextFollowUpAt(),
        lead.getNextAction(), lead.getServicePackage(), lead.getAmountJod(), lead.getPaymentStatus(),
        lead.getSummaryNotes(), lead.getVersion(), lead.getCreatedAt(), lead.getUpdatedAt());
  }

  private B2bAccountResponse accountResponse(B2bAccount account) {
    return new B2bAccountResponse(
        account.getId(), accountCode(account.getId()), account.getOrganization(), account.getType(),
        account.getContactPerson(), account.getJobTitle(), account.getPhone(), account.getEmail(),
        account.getCity(), account.getStudentCount(), account.getSource(), ownerResponse(account.getOwner()),
        account.getStage(), account.getLastContactAt(), account.getNextFollowUpAt(), account.getNextAction(),
        account.getOffer(), account.getEstimatedValueJod(), account.getProposalSentAt(), account.getProbability(),
        account.getSummaryNotes(), account.getVersion(), account.getCreatedAt(), account.getUpdatedAt());
  }

  private ActivityResponse activityResponse(CrmActivity activity) {
    return new ActivityResponse(activity.getId(), activity.getType(), activity.getDetails(),
        ownerResponse(activity.getActor()), activity.getOccurredAt(), activity.getCreatedAt());
  }

  private FollowUpResponse followUpResponse(CrmFollowUp followUp) {
    if (followUp.getStudentLead() != null) {
      var lead = followUp.getStudentLead();
      return new FollowUpResponse(followUp.getId(), CrmTargetType.STUDENT, lead.getId(), leadCode(lead.getId()),
          lead.getFullName(), lead.getPhone(), lead.getStatus().name(), ownerResponse(followUp.getOwner()),
          lead.getLastContactAt(), followUp.getDueAt(), followUp.getAction(), followUp.getStatus(), followUp.getResult(),
          followUp.getCompletedAt(), ownerResponse(followUp.getCompletedBy()), followUp.getVersion(), followUp.getCreatedAt());
    }
    var account = followUp.getB2bAccount();
    return new FollowUpResponse(followUp.getId(), CrmTargetType.B2B, account.getId(), accountCode(account.getId()),
        account.getOrganization(), account.getPhone(), account.getStage().name(), ownerResponse(followUp.getOwner()),
        account.getLastContactAt(), followUp.getDueAt(), followUp.getAction(), followUp.getStatus(), followUp.getResult(),
        followUp.getCompletedAt(), ownerResponse(followUp.getCompletedBy()), followUp.getVersion(), followUp.getCreatedAt());
  }

  private OwnerResponse ownerResponse(User user) {
    if (user == null) return null;
    return new OwnerResponse(user.getId(), user.getFullName(), user.getEmail(),
        user.getRoles().stream().map(Enum::name).sorted().toList());
  }

  private static boolean contactActivity(CrmActivityType type) {
    return type == CrmActivityType.CALL || type == CrmActivityType.WHATSAPP
        || type == CrmActivityType.EMAIL || type == CrmActivityType.MEETING;
  }

  private static void validateManualActivity(CrmActivityType type) {
    if (!MANUAL_ACTIVITY_TYPES.contains(type)) {
      throw new IllegalArgumentException("نوع النشاط محجوز للنظام");
    }
  }

  private static Long id(User user) { return user == null ? null : user.getId(); }
  private static String leadCode(Long id) { return "L-%05d".formatted(id); }
  private static String accountCode(Long id) { return "B2B-%04d".formatted(id); }
  private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  private static String cleanLower(String value) {
    var cleaned = clean(value);
    return cleaned == null ? null : cleaned.toLowerCase(Locale.ROOT);
  }

  static String normalizePhone(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("رقم الهاتف مطلوب");
    var normalized = new StringBuilder();
    var digits = 0;
    for (int index = 0; index < value.length(); index++) {
      var character = value.charAt(index);
      if (character == '+' && normalized.isEmpty()) { normalized.append(character); continue; }
      var digit = Character.digit(character, 10);
      if (digit >= 0) { normalized.append(digit); digits++; continue; }
      if (Character.isWhitespace(character) || character == '-' || character == '.'
          || character == '(' || character == ')') continue;
      throw new IllegalArgumentException("رقم الهاتف غير صالح");
    }
    if (digits < 7 || digits > 15) throw new IllegalArgumentException("رقم الهاتف غير صالح");
    return normalized.toString();
  }

  private static void appendCsv(StringBuilder output, Object... values) {
    for (int index = 0; index < values.length; index++) {
      if (index > 0) output.append(',');
      var value = Objects.toString(values[index], "");
      if (!value.isEmpty() && (value.charAt(0) == '=' || value.charAt(0) == '+'
          || value.charAt(0) == '-' || value.charAt(0) == '@'
          || value.charAt(0) == '\t' || value.charAt(0) == '\r')) {
        value = "'" + value;
      }
      output.append('"').append(value.replace("\"", "\"\"")).append('"');
    }
    output.append('\n');
  }
}
