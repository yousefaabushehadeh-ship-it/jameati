package com.jameati.subscription;

import static com.jameati.subscription.ConsultationDtos.*;

import com.jameati.common.NotFoundException;
import com.jameati.user.User;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsultationService {
  private final ConsultationScheduleRepository schedules;
  private final ConsultationAppointmentRepository appointments;
  private final AnnualMembershipRepository memberships;

  public ConsultationService(ConsultationScheduleRepository schedules,
      ConsultationAppointmentRepository appointments, AnnualMembershipRepository memberships) {
    this.schedules = schedules;
    this.appointments = appointments;
    this.memberships = memberships;
  }

  public MyConsultationsResponse mine(User user) {
    var paid = paidConsultations(user);
    var now = Instant.now();
    return new MyConsultationsResponse(paid.stream().anyMatch(item -> eligible(item, now)),
        paid.stream().map(ConsultationService::progress).toList(),
        appointments.findByMembershipUserIdAndDeletedFalseOrderByStartsAtDesc(user.getId())
            .stream().map(ConsultationService::appointment).toList(),
        scheduleEntity().getTimezone());
  }

  public AvailabilityResponse availability(User user, LocalDate date, int durationMinutes) {
    validateDuration(durationMinutes);
    var now = Instant.now();
    var membership = requireEligible(user, now);
    var schedule = scheduleEntity();
    var zone = ZoneId.of(schedule.getTimezone());
    validateDate(date, zone, now);
    if (!schedule.isEnabled() || appointments.existsByMembershipIdAndStatusAndEndsAtAfterAndDeletedFalse(
        membership.getId(), AppointmentStatus.BOOKED, now)) {
      return new AvailabilityResponse(date, schedule.getTimezone(), List.of());
    }
    var dayStart = date.atStartOfDay(zone).toInstant();
    var dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
    var busy = appointments.overlapping(dayStart, dayEnd.plusSeconds(durationMinutes * 60L));
    var slots = new ArrayList<SlotResponse>();
    // Walk real instants, so DST changes cannot create duplicate or nonexistent local slots.
    for (var start = dayStart; start.isBefore(dayEnd); start = start.plusSeconds(30 * 60)) {
      var candidateStart = start;
      var end = start.plusSeconds(durationMinutes * 60L);
      if (start.isAfter(now) && permitted(schedule, start, end)
          && busy.stream().noneMatch(item -> overlaps(item, candidateStart, end))) {
        slots.add(new SlotResponse(start, end));
      }
    }
    return new AvailabilityResponse(date, schedule.getTimezone(), List.copyOf(slots));
  }

  @Transactional
  public AppointmentResponse book(User user, BookingRequest request) {
    if (request == null || request.startsAt() == null) throw new IllegalArgumentException("وقت الموعد مطلوب");
    validateDuration(request.durationMinutes());
    validateNotes(request.notes(), 2000);
    // Lock an existing singleton row before reading reservations, including when the calendar is empty.
    // Every writer uses this same lock; concurrent requests in other JVMs serialize at the database.
    var schedule = lockCalendar();
    var now = Instant.now();
    var membership = requireEligible(user, now);
    var start = request.startsAt();
    var end = start.plusSeconds(request.durationMinutes() * 60L);
    var zone = ZoneId.of(schedule.getTimezone());
    validateDate(start.atZone(zone).toLocalDate(), zone, now);
    if (!start.isAfter(now) || !permitted(schedule, start, end)) {
      throw new IllegalArgumentException("هذا الموعد خارج أوقات الاستشارة المتاحة");
    }
    if (appointments.existsByMembershipIdAndStatusAndEndsAtAfterAndDeletedFalse(
        membership.getId(), AppointmentStatus.BOOKED, now)) {
      throw new ConsultationConflictException("لديك موعد محجوز بالفعل؛ ألغِه قبل حجز موعد آخر");
    }
    if (!appointments.overlapping(start, end).isEmpty()) {
      throw new ConsultationConflictException("حُجز هذا الموعد للتو؛ اختر موعداً آخر");
    }
    var item = new ConsultationAppointment();
    item.setMembership(membership);
    item.setStartsAt(start);
    item.setEndsAt(end);
    item.setDurationMinutes(request.durationMinutes());
    item.setNotes(trimmed(request.notes()));
    return appointment(appointments.saveAndFlush(item));
  }

  @Transactional
  public AppointmentResponse cancel(User user, Long id) {
    lockCalendar();
    var item = requireAppointment(id);
    if (!item.getMembership().getUser().getId().equals(user.getId())) {
      throw new NotFoundException("الموعد غير موجود");
    }
    if (item.getStatus() == AppointmentStatus.CANCELLED) return appointment(item);
    if (item.getStatus() != AppointmentStatus.BOOKED || !item.getStartsAt().isAfter(Instant.now())) {
      throw new IllegalArgumentException("لا يمكن إلغاء موعد بدأ أو اكتمل");
    }
    item.setStatus(AppointmentStatus.CANCELLED);
    return appointment(item);
  }

  public ScheduleResponse schedule() { return scheduleResponse(scheduleEntity()); }

  @Transactional
  public ScheduleResponse updateSchedule(ScheduleRequest request) {
    if (request == null || request.workingDays() == null || request.workingDays().isEmpty()
        || request.workingDays().size() > 7 || request.workingDays().stream().anyMatch(day -> day == null || day < 1 || day > 7)) {
      throw new IllegalArgumentException("حدد أيام العمل الصحيحة");
    }
    LocalTime start;
    LocalTime end;
    try {
      ZoneId.of(request.timezone());
      start = LocalTime.parse(request.startTime());
      end = LocalTime.parse(request.endTime());
    } catch (DateTimeException | NullPointerException exception) {
      throw new IllegalArgumentException("المنطقة الزمنية أو أوقات العمل غير صالحة");
    }
    if (start.getSecond() != 0 || end.getSecond() != 0 || start.getNano() != 0 || end.getNano() != 0
        || (!request.allDay() && !start.isBefore(end))) {
      throw new IllegalArgumentException("أوقات العمل غير صالحة");
    }
    var schedule = lockCalendar();
    schedule.setEnabled(request.enabled());
    schedule.setAllDay(request.allDay());
    schedule.setTimezone(request.timezone());
    schedule.setWorkingDays(request.workingDays().stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(",")));
    schedule.setStartTime(start.toString());
    schedule.setEndTime(end.toString());
    return scheduleResponse(schedule);
  }

  public AdminConsultationsResponse adminList() {
    return new AdminConsultationsResponse(
        appointments.findByDeletedFalseOrderByStartsAtDesc().stream().map(ConsultationService::appointment).toList(),
        memberships.findByPlanCodeAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            MembershipPlanCode.CONSULTATION, MembershipStatus.PAID).stream().map(ConsultationService::progress).toList());
  }

  @Transactional
  public ProgressResponse updateProgress(Long membershipId, ProgressRequest request) {
    if (request == null || request.status() == null) throw new IllegalArgumentException("حالة المتابعة مطلوبة");
    validateNotes(request.notes(), 4000);
    lockCalendar();
    var item = memberships.findByIdForUpdate(membershipId)
        .filter(membership -> membership.getPlanCode() == MembershipPlanCode.CONSULTATION
            && membership.getStatus() == MembershipStatus.PAID)
        .orElseThrow(() -> new NotFoundException("باقة الاستشارة غير موجودة"));
    item.setConsultationProgress(request.status().name());
    item.setConsultationNotes(trimmed(request.notes()));
    memberships.flush();
    return progress(item);
  }

  @Transactional
  public AppointmentResponse updateStatus(Long id, StatusRequest request) {
    if (request == null || request.status() == null || request.status() == AppointmentStatus.BOOKED) {
      throw new IllegalArgumentException("الحالة المطلوبة غير صالحة");
    }
    lockCalendar();
    var item = requireAppointment(id);
    if (item.getStatus() == request.status()) return appointment(item);
    if (item.getStatus() != AppointmentStatus.BOOKED) throw new IllegalArgumentException("الموعد مغلق بالفعل");
    if (request.status() == AppointmentStatus.COMPLETED && item.getEndsAt().isAfter(Instant.now())) {
      throw new IllegalArgumentException("لا يمكن إكمال الاستشارة قبل انتهاء موعدها");
    }
    item.setStatus(request.status());
    return appointment(item);
  }

  private ConsultationAppointment requireAppointment(Long id) {
    return appointments.findById(id).filter(item -> !item.isDeleted())
        .orElseThrow(() -> new NotFoundException("الموعد غير موجود"));
  }

  private List<AnnualMembership> paidConsultations(User user) {
    return memberships.findByUserIdAndPlanCodeAndStatusAndDeletedFalseOrderByCreatedAtDesc(
        user.getId(), MembershipPlanCode.CONSULTATION, MembershipStatus.PAID);
  }

  private AnnualMembership requireEligible(User user, Instant now) {
    return paidConsultations(user).stream().filter(item -> eligible(item, now)).findFirst()
        .orElseThrow(() -> new AccessDeniedException("حجز الاستشارة متاح بعد اعتماد دفع باقة الاستشارة"));
  }

  private static boolean eligible(AnnualMembership item, Instant now) {
    return item.getStartsAt() != null && !item.getStartsAt().isAfter(now)
        && !"ACHIEVED".equals(item.getConsultationProgress());
  }

  private ConsultationSchedule scheduleEntity() {
    return schedules.findById(1L).orElseThrow(() -> new IllegalStateException("جدول الاستشارات غير مهيأ"));
  }

  private ConsultationSchedule lockCalendar() {
    return schedules.lockCalendar().orElseThrow(() -> new IllegalStateException("جدول الاستشارات غير مهيأ"));
  }

  private static boolean permitted(ConsultationSchedule schedule, Instant start, Instant end) {
    if (!schedule.isEnabled()) return false;
    var zone = ZoneId.of(schedule.getTimezone());
    var localStart = start.atZone(zone);
    if (localStart.getMinute() % 30 != 0 || localStart.getSecond() != 0 || localStart.getNano() != 0) return false;
    var days = workingDays(schedule);
    // Check the entire reserved interval, including the portion after midnight.
    for (var instant = start; instant.isBefore(end); instant = instant.plusSeconds(60)) {
      var local = instant.atZone(zone);
      if (!days.contains(local.getDayOfWeek().getValue())) return false;
      if (!schedule.isAllDay() && (local.toLocalTime().isBefore(LocalTime.parse(schedule.getStartTime()))
          || !local.toLocalTime().isBefore(LocalTime.parse(schedule.getEndTime())))) return false;
    }
    return true;
  }

  private static boolean overlaps(ConsultationAppointment item, Instant start, Instant end) {
    return item.getStartsAt().isBefore(end) && item.getEndsAt().isAfter(start);
  }

  private static void validateDuration(int duration) {
    if (duration != 30 && duration != 60) throw new IllegalArgumentException("مدة الاستشارة 30 أو 60 دقيقة");
  }
  private static void validateDate(LocalDate date, ZoneId zone, Instant now) {
    var today = now.atZone(zone).toLocalDate();
    if (date == null || date.isBefore(today) || date.isAfter(today.plusDays(90))) {
      throw new IllegalArgumentException("اختر موعداً خلال التسعين يوماً القادمة");
    }
  }
  private static List<Integer> workingDays(ConsultationSchedule schedule) {
    return Arrays.stream(schedule.getWorkingDays().split(",")).map(Integer::valueOf).toList();
  }
  private static void validateNotes(String notes, int limit) {
    if (notes != null && notes.length() > limit) throw new IllegalArgumentException("الملاحظات أطول من المسموح");
  }
  private static String trimmed(String text) { return text == null || text.isBlank() ? null : text.trim(); }
  private static ScheduleResponse scheduleResponse(ConsultationSchedule item) {
    return new ScheduleResponse(item.isEnabled(), item.isAllDay(), item.getTimezone(), workingDays(item), item.getStartTime(), item.getEndTime());
  }
  private static AppointmentResponse appointment(ConsultationAppointment item) {
    var user = item.getMembership().getUser();
    return new AppointmentResponse(item.getId(), item.getMembership().getId(), user.getFullName(), user.getEmail(),
        item.getStartsAt(), item.getEndsAt(), item.getDurationMinutes(), item.getStatus(), item.getNotes());
  }
  private static ProgressResponse progress(AnnualMembership item) {
    var user = item.getUser();
    return new ProgressResponse(item.getId(), user.getFullName(), user.getEmail(),
        ProgressStatus.valueOf(item.getConsultationProgress()), item.getConsultationNotes(), item.getUpdatedAt());
  }
}
