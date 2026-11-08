package com.jameati.subscription;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class ScholarshipSubscriptionDtos {
  private ScholarshipSubscriptionDtos() {}

  public record CheckoutRequest(@NotNull Long opportunityId) {}

  public record ApplicationInformationRequest(
      @NotBlank @Size(max = 160) String fullName,
      @NotBlank @Size(max = 40) String phone,
      @Size(max = 40) String whatsapp,
      @NotBlank @Size(max = 100) String nationality,
      @NotBlank @Size(max = 100) String residenceCountry,
      @NotNull @Past LocalDate dateOfBirth,
      @NotBlank @Size(max = 100) String degreeLevel,
      @Size(max = 180) String university,
      @NotBlank @Size(max = 180) String speciality,
      @Size(max = 20) String graduationYear,
      @Size(max = 40) String gpa,
      @Size(max = 80) String englishLevel,
      @Size(max = 5000) String notes,
      @Size(max = 5000) String documentsText) {}

  public record AdminReviewRequest(
      @NotNull ScholarshipApplicationStatus status,
      @Size(max = 5000) String reviewNotes) {}

  public record SubscriptionResponse(
      Long id,
      Long opportunityId,
      String opportunitySlug,
      String opportunityTitle,
      String opportunityOrganization,
      String studentName,
      String studentEmail,
      BigDecimal amount,
      String currency,
      PaymentStatus paymentStatus,
      ScholarshipApplicationStatus applicationStatus,
      String paymentProvider,
      String paymentReference,
      Instant paidAt,
      Instant submittedAt,
      Instant reviewedAt,
      Instant contactedAt,
      String fullName,
      String phone,
      String whatsapp,
      String nationality,
      String residenceCountry,
      LocalDate dateOfBirth,
      String degreeLevel,
      String university,
      String speciality,
      String graduationYear,
      String gpa,
      String englishLevel,
      String notes,
      String documentsText,
      String reviewNotes,
      String reviewedBy,
      Instant createdAt,
      Instant updatedAt) {}
}
