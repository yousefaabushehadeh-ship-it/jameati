package com.jameati.subscription;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ConsultationDtos {
  private ConsultationDtos() {}
  public enum AppointmentStatus { BOOKED, COMPLETED, CANCELLED }
  public enum ProgressStatus { WAITING, APPLICATION_IN_PROGRESS, FOLLOW_UP, ACHIEVED }
  public record ScheduleResponse(boolean enabled, boolean allDay, String timezone,
      List<Integer> workingDays, String startTime, String endTime) {}
  public record ScheduleRequest(boolean enabled, boolean allDay,
      @NotBlank @Size(max = 60) String timezone,
      @NotNull @Size(min = 1, max = 7) List<@Min(1) @Max(7) Integer> workingDays,
      @NotBlank String startTime, @NotBlank String endTime) {}
  public record SlotResponse(Instant startsAt, Instant endsAt) {}
  public record AvailabilityResponse(LocalDate date, String timezone, List<SlotResponse> slots) {}
  public record BookingRequest(@NotNull Instant startsAt, int durationMinutes, @Size(max = 2000) String notes) {}
  public record StatusRequest(@NotNull AppointmentStatus status) {}
  public record ProgressRequest(@NotNull ProgressStatus status, @Size(max = 4000) String notes) {}
  public record AppointmentResponse(Long id, Long membershipId, String userName, String userEmail,
      Instant startsAt, Instant endsAt, int durationMinutes, AppointmentStatus status, String notes) {}
  public record ProgressResponse(Long membershipId, String userName, String userEmail,
      ProgressStatus status, String notes, Instant updatedAt) {}
  public record MyConsultationsResponse(boolean eligible, List<ProgressResponse> progress,
      List<AppointmentResponse> appointments, String timezone) {}
  public record AdminConsultationsResponse(List<AppointmentResponse> appointments, List<ProgressResponse> progress) {}
}
