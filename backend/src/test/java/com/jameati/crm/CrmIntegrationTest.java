package com.jameati.crm;

import static com.jameati.crm.CrmDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jameati.user.RoleName;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CrmIntegrationTest {
  @Autowired CrmService service;
  @Autowired StudentLeadRepository leads;
  @Autowired CrmFollowUpRepository followUps;
  @Autowired UserRepository users;
  @Autowired MockMvc mvc;

  private User owner;

  @BeforeEach
  void createOwner() {
    owner = new User(
        "crm-owner-" + UUID.randomUUID() + "@jameati.test", "unused", "CRM test owner");
    owner.getRoles().add(RoleName.SALES);
    owner = users.saveAndFlush(owner);
  }

  @Test
  void activeLeadRequiresANextFollowUpAndNextAction() {
    assertThatThrownBy(() -> service.createLead(
            leadRequest(LeadStatus.NEW, null, "اتصال أولي"), owner.getEmail()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("يجب تحديد موعد المتابعة القادمة");
  }

  @Test
  void repeatedSignupReusesTheLeadAndPendingFollowUp() {
    var student = users.saveAndFlush(new User(
        "crm-student-" + UUID.randomUUID() + "@jameati.test", "unused", "طالب جديد"));

    var first = service.ensureLeadForRegistration(student, "+962 79 123 4567");
    var second = service.ensureLeadForRegistration(student, "+962791234567");

    assertThat(second.id()).isEqualTo(first.id());
    assertThat(leads.findByUserIdAndDeletedFalse(student.getId())).isPresent();
    assertThat(followUps.findByStudentLeadIdAndStatusAndDeletedFalseOrderByDueAtAsc(
        first.id(), FollowUpStatus.PENDING)).hasSize(1);
  }

  @Test
  void reschedulingThenCompletingKeepsOneActionablePendingFollowUp() {
    var originalDueAt = Instant.now().plusSeconds(3_600);
    var lead = service.createLead(
        leadRequest(LeadStatus.NEW, originalDueAt, "اتصال أولي"), owner.getEmail());
    var original = followUps.findByStudentLeadIdAndStatusAndDeletedFalseOrderByDueAtAsc(
        lead.id(), FollowUpStatus.PENDING).getFirst();

    var rescheduledAt = originalDueAt.plusSeconds(3_600);
    service.rescheduleFollowUp(original.getId(),
        new RescheduleFollowUpRequest(rescheduledAt, "إعادة الاتصال"), owner.getEmail());

    var rescheduledLead = leads.findByIdAndDeletedFalse(lead.id()).orElseThrow();
    assertThat(original.getDueAt()).isEqualTo(rescheduledAt);
    assertThat(original.getAction()).isEqualTo("إعادة الاتصال");
    assertThat(rescheduledLead.getNextFollowUpAt()).isEqualTo(rescheduledAt);
    assertThat(rescheduledLead.getNextAction()).isEqualTo("إعادة الاتصال");

    var nextDueAt = rescheduledAt.plusSeconds(86_400);
    service.completeFollowUp(original.getId(),
        new CompleteFollowUpRequest("تم التواصل", nextDueAt, "إرسال العرض"), owner.getEmail());

    var pending = followUps.findByStudentLeadIdAndStatusAndDeletedFalseOrderByDueAtAsc(
        lead.id(), FollowUpStatus.PENDING);
    assertThat(original.getStatus()).isEqualTo(FollowUpStatus.DONE);
    assertThat(original.getResult()).isEqualTo("تم التواصل");
    assertThat(original.getCompletedAt()).isNotNull();
    assertThat(pending).singleElement().satisfies(next -> {
      assertThat(next.getId()).isNotEqualTo(original.getId());
      assertThat(next.getDueAt()).isEqualTo(nextDueAt);
      assertThat(next.getAction()).isEqualTo("إرسال العرض");
    });
    assertThat(rescheduledLead.getNextFollowUpAt()).isEqualTo(nextDueAt);
    assertThat(rescheduledLead.getNextAction()).isEqualTo("إرسال العرض");
    assertThat(rescheduledLead.getLastContactAt()).isNotNull();
  }

  @Test
  void terminalStatusCancelsPendingFollowUpAndClearsNextAction() {
    var dueAt = Instant.now().plusSeconds(3_600);
    var lead = service.createLead(
        leadRequest(LeadStatus.NEW, dueAt, "اتصال أولي"), owner.getEmail());
    var pending = followUps.findByStudentLeadIdAndStatusAndDeletedFalseOrderByDueAtAsc(
        lead.id(), FollowUpStatus.PENDING).getFirst();

    service.updateLead(lead.id(),
        leadRequest(LeadStatus.LOST, null, null), owner.getEmail());

    var terminalLead = leads.findByIdAndDeletedFalse(lead.id()).orElseThrow();
    assertThat(pending.getStatus()).isEqualTo(FollowUpStatus.CANCELLED);
    assertThat(terminalLead.getNextFollowUpAt()).isNull();
    assertThat(terminalLead.getNextAction()).isNull();
    assertThat(followUps.findByStudentLeadIdAndStatusAndDeletedFalseOrderByDueAtAsc(
        lead.id(), FollowUpStatus.PENDING)).isEmpty();
  }

  @Test
  void staffCannotForgeSystemTimelineEvents() {
    var lead = service.createLead(
        leadRequest(LeadStatus.NEW, Instant.now().plusSeconds(3_600), "اتصال أولي"),
        owner.getEmail());

    assertThatThrownBy(() -> service.addLeadActivity(
            lead.id(),
            new ActivityRequest(CrmActivityType.OWNER_CHANGED, "حدث مصطنع", null),
            owner.getEmail()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("نوع النشاط محجوز للنظام");
  }

  @Test
  void csvExportNeutralizesSpreadsheetFormulaPrefixes() {
    service.createLead(
        leadRequest(LeadStatus.NEW, Instant.now().plusSeconds(3_600), "اتصال أولي"),
        owner.getEmail());

    var csv = service.exportLeadsCsv("", null, null, null, null, null);

    assertThat(csv).contains("\"'+96279");
  }

  @ParameterizedTest
  @ValueSource(strings = {"SALES", "OPERATIONS", "SECRETARY", "ADMIN", "SUPER_ADMIN"})
  void crmRolesCanAccessTheCrm(String role) throws Exception {
    mvc.perform(get("/api/v1/admin/crm/dashboard")
            .with(user("allowed-" + role + "@jameati.test").roles(role)))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"USER", "EDITOR", "DATA_ENTRY"})
  void unrelatedRolesCannotAccessTheCrm(String role) throws Exception {
    mvc.perform(get("/api/v1/admin/crm/dashboard")
            .with(user("denied-" + role + "@jameati.test").roles(role)))
        .andExpect(status().isForbidden());
  }

  @Test
  void anonymousCannotAccessTheCrm() throws Exception {
    mvc.perform(get("/api/v1/admin/crm/dashboard"))
        .andExpect(status().isUnauthorized());
  }

  private StudentLeadRequest leadRequest(
      LeadStatus status, Instant nextFollowUpAt, String nextAction) {
    var unique = UUID.randomUUID().toString();
    var phoneSuffix = "%07d".formatted(Math.floorMod(unique.hashCode(), 10_000_000));
    return new StudentLeadRequest(
        "طالب اختبار",
        "+96279" + phoneSuffix,
        "crm-lead-" + unique + "@jameati.test",
        LocalDate.of(2004, 1, 1),
        "Amman",
        "University",
        "IT",
        "B2",
        "Remote Job",
        "Remote Jobs",
        "Website",
        owner.getId(),
        status,
        null,
        nextFollowUpAt,
        nextAction,
        "Jamiaati Plus",
        BigDecimal.valueOf(5),
        CrmPaymentStatus.PENDING,
        "اختبار تكامل");
  }
}
