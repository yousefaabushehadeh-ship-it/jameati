package com.jameati.crm;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class CrmDtos {
  private CrmDtos() {}

  public record OwnerResponse(Long id, String fullName, String email, List<String> roles) {}

  public record StudentLeadRequest(
      @NotBlank @Size(max = 160) String fullName,
      @NotBlank @Size(max = 40) String phone,
      @NotBlank @Email @Size(max = 190) String email,
      LocalDate dateOfBirth,
      @Size(max = 100) String city,
      @Size(max = 100) String educationLevel,
      @Size(max = 180) String major,
      @Size(max = 60) String englishLevel,
      @Size(max = 120) String mainNeed,
      @Size(max = 180) String interest,
      @NotBlank @Size(max = 80) String source,
      @NotNull Long ownerId,
      @NotNull LeadStatus status,
      Instant lastContactAt,
      Instant nextFollowUpAt,
      @Size(max = 500) String nextAction,
      @Size(max = 180) String servicePackage,
      @DecimalMin(value = "0.0") BigDecimal amountJod,
      @NotNull CrmPaymentStatus paymentStatus,
      @Size(max = 2000) String summaryNotes) {}

  public record StudentLeadResponse(
      Long id,
      String code,
      Long userId,
      String fullName,
      String phone,
      String email,
      LocalDate dateOfBirth,
      Integer age,
      String city,
      String educationLevel,
      String major,
      String englishLevel,
      String mainNeed,
      String interest,
      String source,
      OwnerResponse owner,
      LeadStatus status,
      Instant lastContactAt,
      Instant nextFollowUpAt,
      String nextAction,
      String servicePackage,
      BigDecimal amountJod,
      CrmPaymentStatus paymentStatus,
      String summaryNotes,
      long version,
      Instant createdAt,
      Instant updatedAt) {}

  public record B2bAccountRequest(
      @NotBlank @Size(max = 200) String organization,
      @NotNull OrganizationType type,
      @NotBlank @Size(max = 160) String contactPerson,
      @Size(max = 160) String jobTitle,
      @NotBlank @Size(max = 40) String phone,
      @Email @Size(max = 190) String email,
      @Size(max = 100) String city,
      @PositiveOrZero Integer studentCount,
      @NotBlank @Size(max = 100) String source,
      @NotNull Long ownerId,
      @NotNull B2bStage stage,
      Instant lastContactAt,
      Instant nextFollowUpAt,
      @Size(max = 500) String nextAction,
      @Size(max = 500) String offer,
      @DecimalMin(value = "0.0") BigDecimal estimatedValueJod,
      Instant proposalSentAt,
      @Min(0) @Max(100) int probability,
      @Size(max = 2000) String summaryNotes) {}

  public record B2bAccountResponse(
      Long id,
      String code,
      String organization,
      OrganizationType type,
      String contactPerson,
      String jobTitle,
      String phone,
      String email,
      String city,
      Integer studentCount,
      String source,
      OwnerResponse owner,
      B2bStage stage,
      Instant lastContactAt,
      Instant nextFollowUpAt,
      String nextAction,
      String offer,
      BigDecimal estimatedValueJod,
      Instant proposalSentAt,
      int probability,
      String summaryNotes,
      long version,
      Instant createdAt,
      Instant updatedAt) {}

  public record ActivityRequest(
      @NotNull CrmActivityType type,
      @NotBlank @Size(max = 3000) String details,
      Instant occurredAt) {}

  public record ActivityResponse(
      Long id,
      CrmActivityType type,
      String details,
      OwnerResponse actor,
      Instant occurredAt,
      Instant createdAt) {}

  public record StudentLeadDetail(StudentLeadResponse lead, List<ActivityResponse> timeline) {}
  public record B2bAccountDetail(B2bAccountResponse account, List<ActivityResponse> timeline) {}

  public record FollowUpResponse(
      Long id,
      CrmTargetType targetType,
      Long targetId,
      String targetCode,
      String name,
      String phone,
      String statusOrStage,
      OwnerResponse owner,
      Instant lastContactAt,
      Instant dueAt,
      String action,
      FollowUpStatus status,
      String result,
      Instant completedAt,
      OwnerResponse completedBy,
      long version,
      Instant createdAt) {}

  public record CompleteFollowUpRequest(
      @NotBlank @Size(max = 2000) String result,
      Instant nextFollowUpAt,
      @Size(max = 500) String nextAction) {}

  public record RescheduleFollowUpRequest(
      @NotNull Instant dueAt,
      @NotBlank @Size(max = 500) String action) {}

  public record PipelineCount(String key, long count) {}

  public record DashboardResponse(
      long totalStudentLeads,
      long newStudentLeadsToday,
      long unassignedStudentLeads,
      long followUpsToday,
      long overdueFollowUps,
      long paidStudentLeads,
      BigDecimal revenueJod,
      long totalB2bAccounts,
      long newB2bAccountsToday,
      List<PipelineCount> studentPipeline,
      List<PipelineCount> b2bPipeline,
      Map<String, Long> activityToday) {}
}
