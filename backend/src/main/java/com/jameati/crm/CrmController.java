package com.jameati.crm;

import static com.jameati.crm.CrmDtos.*;

import com.jameati.common.ApiResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/crm")
@PreAuthorize("hasAnyRole('SALES','OPERATIONS','SECRETARY','ADMIN','SUPER_ADMIN')")
public class CrmController {
  private final CrmService service;

  public CrmController(CrmService service) {
    this.service = service;
  }

  @GetMapping("/dashboard")
  ApiResponse<DashboardResponse> dashboard() {
    return ApiResponse.ok(service.dashboard());
  }

  @GetMapping("/owners")
  ApiResponse<List<OwnerResponse>> owners() {
    return ApiResponse.ok(service.owners());
  }

  @GetMapping("/leads")
  ApiResponse<Page<StudentLeadResponse>> leads(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) LeadStatus status,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(required = false) CrmPaymentStatus paymentStatus,
      @RequestParam(required = false) FollowUpBucket followUp,
      @PageableDefault(size = 25, sort = "updatedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ApiResponse.ok(service.leads(q, status, source, ownerId, paymentStatus, followUp, pageable));
  }

  @GetMapping("/leads/export.csv")
  ResponseEntity<byte[]> exportLeads(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) LeadStatus status,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(required = false) CrmPaymentStatus paymentStatus,
      @RequestParam(required = false) FollowUpBucket followUp) {
    var bytes = service.exportLeadsCsv(q, status, source, ownerId, paymentStatus, followUp)
        .getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=jamiaati-student-crm.csv")
        .body(bytes);
  }

  @GetMapping("/leads/{id}")
  ApiResponse<StudentLeadDetail> lead(@PathVariable Long id) {
    return ApiResponse.ok(service.lead(id));
  }

  @PostMapping("/leads")
  ResponseEntity<ApiResponse<StudentLeadResponse>> createLead(
      @Valid @RequestBody StudentLeadRequest request, Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.created(service.createLead(request, principal.getName())));
  }

  @PutMapping("/leads/{id}")
  ApiResponse<StudentLeadResponse> updateLead(
      @PathVariable Long id,
      @Valid @RequestBody StudentLeadRequest request,
      Principal principal) {
    return ApiResponse.ok(service.updateLead(id, request, principal.getName()));
  }

  @PostMapping("/leads/{id}/activities")
  ResponseEntity<ApiResponse<ActivityResponse>> addLeadActivity(
      @PathVariable Long id,
      @Valid @RequestBody ActivityRequest request,
      Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.created(service.addLeadActivity(id, request, principal.getName())));
  }

  @GetMapping("/accounts")
  ApiResponse<Page<B2bAccountResponse>> accounts(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) B2bStage stage,
      @RequestParam(required = false) OrganizationType type,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(required = false) FollowUpBucket followUp,
      @PageableDefault(size = 25, sort = "updatedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ApiResponse.ok(service.accounts(q, stage, type, ownerId, followUp, pageable));
  }

  @GetMapping("/accounts/{id}")
  ApiResponse<B2bAccountDetail> account(@PathVariable Long id) {
    return ApiResponse.ok(service.account(id));
  }

  @PostMapping("/accounts")
  ResponseEntity<ApiResponse<B2bAccountResponse>> createAccount(
      @Valid @RequestBody B2bAccountRequest request, Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.created(service.createAccount(request, principal.getName())));
  }

  @PutMapping("/accounts/{id}")
  ApiResponse<B2bAccountResponse> updateAccount(
      @PathVariable Long id,
      @Valid @RequestBody B2bAccountRequest request,
      Principal principal) {
    return ApiResponse.ok(service.updateAccount(id, request, principal.getName()));
  }

  @PostMapping("/accounts/{id}/activities")
  ResponseEntity<ApiResponse<ActivityResponse>> addAccountActivity(
      @PathVariable Long id,
      @Valid @RequestBody ActivityRequest request,
      Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.created(service.addAccountActivity(id, request, principal.getName())));
  }

  @GetMapping("/follow-ups")
  ApiResponse<Page<FollowUpResponse>> followUps(
      @RequestParam(defaultValue = "TODAY") FollowUpBucket bucket,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(required = false) CrmTargetType targetType,
      @PageableDefault(size = 50, sort = "dueAt", direction = Sort.Direction.ASC)
          Pageable pageable) {
    return ApiResponse.ok(service.followUps(bucket, ownerId, targetType, pageable));
  }

  @PostMapping("/follow-ups/{id}/complete")
  ApiResponse<FollowUpResponse> completeFollowUp(
      @PathVariable Long id,
      @Valid @RequestBody CompleteFollowUpRequest request,
      Principal principal) {
    return ApiResponse.ok(service.completeFollowUp(id, request, principal.getName()));
  }

  @PatchMapping("/follow-ups/{id}/reschedule")
  ApiResponse<FollowUpResponse> rescheduleFollowUp(
      @PathVariable Long id,
      @Valid @RequestBody RescheduleFollowUpRequest request,
      Principal principal) {
    return ApiResponse.ok(service.rescheduleFollowUp(id, request, principal.getName()));
  }
}
