package com.jameati.subscription;

import static com.jameati.subscription.ConsultationDtos.*;
import static org.assertj.core.api.Assertions.*;

import com.jameati.common.NotFoundException;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class ConsultationBookingIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ConsultationService service;
  @Autowired ConsultationAppointmentRepository appointments;
  @Autowired AnnualMembershipRepository memberships;
  @Autowired UserRepository users;
  @Autowired TransactionTemplate transactions;
  @Autowired ContentAccessService contentAccess;
  private final List<Long> userIds = new ArrayList<>();
  private ScheduleResponse previousSchedule;
  private Instant tomorrow;

  @BeforeEach
  void setup() {
    previousSchedule = service.schedule();
    service.updateSchedule(new ScheduleRequest(true, true, "Asia/Amman", List.of(1, 2, 3, 4, 5, 6, 7), "00:00", "23:59"));
    tomorrow = LocalDate.now(ZoneId.of("Asia/Amman")).plusDays(1).atTime(10, 0).atZone(ZoneId.of("Asia/Amman")).toInstant();
  }

  @AfterEach
  void cleanup() {
    transactions.executeWithoutResult(status -> {
      for (var id : userIds) {
        appointments.deleteAll(appointments.findByMembershipUserIdAndDeletedFalseOrderByStartsAtDesc(id));
        appointments.flush();
        memberships.deleteAll(memberships.findByUserIdAndDeletedFalse(id, org.springframework.data.domain.Pageable.unpaged()));
        memberships.flush();
        users.deleteById(id);
      }
    });
    service.updateSchedule(new ScheduleRequest(previousSchedule.enabled(), previousSchedule.allDay(), previousSchedule.timezone(),
        previousSchedule.workingDays(), previousSchedule.startTime(), previousSchedule.endTime()));
  }

  @Test
  void anonymousCannotReadAppointmentsAndMembersCannotAccessAdminCalendar() throws Exception {
    mvc.perform(get("/api/v1/me/consultations")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/admin/consultations").with(user("member@example.com").roles("USER")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/consultations/schedule").with(user("admin@example.com").roles("ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  void simultaneousOverlappingBookingsCommitExactlyOneReservation() throws Exception {
    var first = customer(MembershipPlanCode.CONSULTATION, MembershipStatus.PAID);
    var second = customer(MembershipPlanCode.CONSULTATION, MembershipStatus.PAID);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures = List.of(
          executor.submit(() -> concurrentBook(first, tomorrow, 60, ready, start)),
          executor.submit(() -> concurrentBook(second, tomorrow.plusSeconds(1800), 30, ready, start)));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      var outcomes = List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
      assertThat(outcomes).containsExactlyInAnyOrder("BOOKED", "CONFLICT");
      assertThat(appointments.overlapping(tomorrow, tomorrow.plusSeconds(3600))).hasSize(1);
    }
  }

  @Test
  void completedPaymentIsRequiredAndContentPlansCannotBook() {
    var pending = customer(MembershipPlanCode.CONSULTATION, MembershipStatus.PENDING);
    var annual = customer(MembershipPlanCode.ANNUAL, MembershipStatus.PAID);
    assertThatThrownBy(() -> service.book(pending, new BookingRequest(tomorrow, 30, null)))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.availability(annual, tomorrow.atZone(ZoneId.of("Asia/Amman")).toLocalDate(), 30))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void adjacentAppointmentsAreAllowedWhileCrossMidnightOverlapIsBlocked() {
    var first = customer(MembershipPlanCode.CONSULTATION, MembershipStatus.PAID);
    var second = customer(MembershipPlanCode.CONSULTATION, MembershipStatus.PAID);
    var third = customer(MembershipPlanCode.CONSULTATION, MembershipStatus.PAID);
    var late = tomorrow.atZone(ZoneId.of("Asia/Amman")).withHour(23).withMinute(30).toInstant();
    var reserved = service.book(first, new BookingRequest(late, 60, null));
    assertThat(reserved.endsAt()).isEqualTo(late.plusSeconds(3600));
    assertThatThrownBy(() -> service.book(second, new BookingRequest(late.plusSeconds(1800), 30, null)))
        .isInstanceOf(ConsultationConflictException.class);
    assertThat(service.book(third, new BookingRequest(late.plusSeconds(3600), 30, null)).status())
        .isEqualTo(AppointmentStatus.BOOKED);
  }

  @Test
  void usersCannotCancelOthersAndCancellationReleasesSlot() {
    var first = customer(MembershipPlanCode.CONSULTATION, MembershipStatus.PAID);
    var second = customer(MembershipPlanCode.CONSULTATION, MembershipStatus.PAID);
    var reserved = service.book(first, new BookingRequest(tomorrow, 30, null));
    assertThatThrownBy(() -> service.cancel(second, reserved.id())).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.book(first, new BookingRequest(tomorrow.plusSeconds(3600), 30, null)))
        .isInstanceOf(ConsultationConflictException.class);
    service.cancel(first, reserved.id());
    assertThat(service.book(second, new BookingRequest(tomorrow, 30, null)).status()).isEqualTo(AppointmentStatus.BOOKED);
  }

  @Test
  void invalidDurationPastMisalignedAndBeyondHorizonRequestsAreRejected() {
    var user = customer(MembershipPlanCode.CONSULTATION, MembershipStatus.PAID);
    assertThatThrownBy(() -> service.book(user, new BookingRequest(tomorrow, 45, null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.book(user, new BookingRequest(tomorrow.minusSeconds(3 * 86400), 30, null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.book(user, new BookingRequest(tomorrow.plusSeconds(1), 30, null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.book(user, new BookingRequest(tomorrow.plusSeconds(91 * 86400), 30, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void consultationDoesNotGrantContentAccessAndFollowupPersistsUntilAchieved() {
    var user = customer(MembershipPlanCode.CONSULTATION, MembershipStatus.PAID);
    assertThat(contentAccess.resolve(user).fullAccess()).isFalse();
    var membershipId = service.mine(user).progress().getFirst().membershipId();
    service.updateProgress(membershipId, new ProgressRequest(ProgressStatus.FOLLOW_UP, "Following the application"));
    assertThat(service.mine(user).eligible()).isTrue();
    assertThat(service.mine(user).progress().getFirst().notes()).isEqualTo("Following the application");
    service.updateProgress(membershipId, new ProgressRequest(ProgressStatus.ACHIEVED, "Offer received"));
    assertThat(service.mine(user).eligible()).isFalse();
    assertThatThrownBy(() -> service.book(user, new BookingRequest(tomorrow, 30, null)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void allHoursInclude48HalfHourStartsAndAvailabilityOmitsConflicts() {
    var first = customer(MembershipPlanCode.CONSULTATION, MembershipStatus.PAID);
    var second = customer(MembershipPlanCode.CONSULTATION, MembershipStatus.PAID);
    var date = tomorrow.atZone(ZoneId.of("Asia/Amman")).toLocalDate();
    assertThat(service.availability(first, date, 30).slots()).hasSize(48);
    service.book(second, new BookingRequest(tomorrow, 60, null));
    assertThat(service.availability(first, date, 30).slots()).hasSize(46)
        .noneMatch(slot -> slot.startsAt().equals(tomorrow) || slot.startsAt().equals(tomorrow.plusSeconds(1800)));
    service.updateSchedule(new ScheduleRequest(false, true, "Asia/Amman", List.of(1, 2, 3, 4, 5, 6, 7), "00:00", "23:59"));
    assertThat(service.availability(first, date, 30).slots()).isEmpty();
    assertThatThrownBy(() -> service.book(first, new BookingRequest(tomorrow.plusSeconds(7200), 30, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private String concurrentBook(User user, Instant startsAt, int duration, CountDownLatch ready, CountDownLatch start) throws Exception {
    ready.countDown();
    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test did not start");
    try {
      service.book(user, new BookingRequest(startsAt, duration, null));
      return "BOOKED";
    } catch (ConsultationConflictException exception) {
      return "CONFLICT";
    }
  }

  private User customer(MembershipPlanCode code, MembershipStatus status) {
    return transactions.execute(transaction -> {
      var user = users.saveAndFlush(new User("consultation-" + UUID.randomUUID() + "@example.com", "hash", "Consultation test"));
      userIds.add(user.getId());
      var membership = new AnnualMembership();
      membership.setUser(user);
      membership.setPlanCode(code);
      membership.setBillingPeriodMonths(code.months());
      membership.setAmount(code.price());
      membership.setCurrency("JOD");
      membership.setStatus(status);
      membership.setPaymentProvider("CLIQ");
      if (status == MembershipStatus.PAID) {
        membership.setPaidAt(Instant.now());
        membership.setStartsAt(Instant.now().minusSeconds(60));
        if (code.contentAccess()) membership.setExpiresAt(Instant.now().plusSeconds(30 * 86400));
      }
      memberships.saveAndFlush(membership);
      return user;
    });
  }
}
