package com.jameati.subscription;

import static com.jameati.subscription.ConsultationDtos.*;

import com.jameati.common.ApiResponse;
import com.jameati.me.CurrentUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class ConsultationController {
  private final ConsultationService consultations;
  private final CurrentUserService users;
  public ConsultationController(ConsultationService consultations, CurrentUserService users) {
    this.consultations = consultations;
    this.users = users;
  }
  @GetMapping("/api/v1/me/consultations")
  ApiResponse<MyConsultationsResponse> mine(Principal principal) {
    return ApiResponse.ok(consultations.mine(users.require(principal)));
  }
  @GetMapping("/api/v1/me/consultations/availability")
  ApiResponse<AvailabilityResponse> availability(Principal principal, @RequestParam LocalDate date,
      @RequestParam(defaultValue = "30") int durationMinutes) {
    return ApiResponse.ok(consultations.availability(users.require(principal), date, durationMinutes));
  }
  @PostMapping("/api/v1/me/consultations/appointments")
  ApiResponse<AppointmentResponse> book(Principal principal, @Valid @RequestBody BookingRequest request) {
    return ApiResponse.created(consultations.book(users.require(principal), request));
  }
  @PostMapping("/api/v1/me/consultations/appointments/{id}/cancel")
  ApiResponse<AppointmentResponse> cancel(Principal principal, @PathVariable Long id) {
    return ApiResponse.ok(consultations.cancel(users.require(principal), id));
  }
  @GetMapping("/api/v1/admin/consultations")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ApiResponse<AdminConsultationsResponse> adminList() { return ApiResponse.ok(consultations.adminList()); }
  @GetMapping("/api/v1/admin/consultations/schedule")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ApiResponse<ScheduleResponse> schedule() { return ApiResponse.ok(consultations.schedule()); }
  @PutMapping("/api/v1/admin/consultations/schedule")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ApiResponse<ScheduleResponse> updateSchedule(@Valid @RequestBody ScheduleRequest request) {
    return ApiResponse.ok(consultations.updateSchedule(request));
  }
  @PutMapping("/api/v1/admin/consultations/{membershipId}/progress")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ApiResponse<ProgressResponse> progress(@PathVariable Long membershipId, @Valid @RequestBody ProgressRequest request) {
    return ApiResponse.ok(consultations.updateProgress(membershipId, request));
  }
  @PatchMapping("/api/v1/admin/consultations/appointments/{id}/status")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ApiResponse<AppointmentResponse> status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
    return ApiResponse.ok(consultations.updateStatus(id, request));
  }
}
