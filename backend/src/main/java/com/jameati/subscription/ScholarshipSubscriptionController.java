package com.jameati.subscription;

import static com.jameati.subscription.ScholarshipSubscriptionDtos.*;

import com.jameati.common.ApiResponse;
import com.jameati.me.CurrentUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class ScholarshipSubscriptionController {
  private final ScholarshipSubscriptionService service;
  private final CurrentUserService currentUsers;

  public ScholarshipSubscriptionController(ScholarshipSubscriptionService service, CurrentUserService currentUsers) {
    this.service = service;
    this.currentUsers = currentUsers;
  }

  @PostMapping("/api/v1/me/scholarship-subscriptions/checkout")
  ResponseEntity<ApiResponse<SubscriptionResponse>> checkout(@Valid @RequestBody CheckoutRequest request, Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.checkout(currentUsers.require(principal), request.opportunityId())));
  }

  @PostMapping("/api/v1/me/scholarship-subscriptions/{id}/mock-payment")
  ApiResponse<SubscriptionResponse> mockPayment(@PathVariable Long id, Principal principal) {
    return ApiResponse.ok(service.mockPay(currentUsers.require(principal), id));
  }

  @PutMapping("/api/v1/me/scholarship-subscriptions/{id}/application")
  ApiResponse<SubscriptionResponse> submit(@PathVariable Long id, @Valid @RequestBody ApplicationInformationRequest request, Principal principal) {
    return ApiResponse.ok(service.submit(currentUsers.require(principal), id, request));
  }

  @GetMapping("/api/v1/me/scholarship-subscriptions")
  ApiResponse<Page<SubscriptionResponse>> mine(Principal principal, @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ApiResponse.ok(service.mine(currentUsers.require(principal), pageable));
  }

  @GetMapping("/api/v1/me/scholarship-subscriptions/{id}")
  ApiResponse<SubscriptionResponse> mineDetail(@PathVariable Long id, Principal principal) {
    return ApiResponse.ok(service.mineDetail(currentUsers.require(principal), id));
  }

  @GetMapping("/api/v1/admin/scholarship-subscriptions")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ApiResponse<Page<SubscriptionResponse>> adminList(@RequestParam(required = false) ScholarshipApplicationStatus status, @PageableDefault(size = 25, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ApiResponse.ok(service.adminList(status, pageable));
  }

  @GetMapping("/api/v1/admin/scholarship-subscriptions/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ApiResponse<SubscriptionResponse> adminDetail(@PathVariable Long id) {
    return ApiResponse.ok(service.adminDetail(id));
  }

  @PatchMapping("/api/v1/admin/scholarship-subscriptions/{id}/review")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ApiResponse<SubscriptionResponse> review(@PathVariable Long id, @Valid @RequestBody AdminReviewRequest request, Principal principal) {
    return ApiResponse.ok(service.review(id, request, principal.getName()));
  }
}
