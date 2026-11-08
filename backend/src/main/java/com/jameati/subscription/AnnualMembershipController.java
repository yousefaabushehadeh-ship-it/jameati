package com.jameati.subscription;

import static com.jameati.subscription.MembershipDtos.*;

import com.jameati.common.ApiResponse;
import com.jameati.me.CurrentUserService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AnnualMembershipController {
  private final AnnualMembershipService memberships;
  private final ContentAccessService access;
  private final CurrentUserService currentUsers;
  private final boolean mockPaymentEnabled;

  public AnnualMembershipController(
      AnnualMembershipService memberships,
      ContentAccessService access,
      CurrentUserService currentUsers,
      @Value("${app.membership.mock-payment-enabled:false}") boolean mockPaymentEnabled) {
    this.memberships = memberships;
    this.access = access;
    this.currentUsers = currentUsers;
    this.mockPaymentEnabled = mockPaymentEnabled;
  }

  @GetMapping("/api/v1/public/membership-plan")
  ApiResponse<MembershipPlanResponse> plan(Principal principal) {
    return ApiResponse.ok(memberships.plan(access.resolve(principal), mockPaymentEnabled));
  }

  @PostMapping(
      value = "/api/v1/me/memberships/checkout",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<ApiResponse<MembershipResponse>> checkout(
      @Valid @RequestBody MembershipCheckoutRequest request,
      Principal principal) {
    var membership = memberships.checkout(currentUsers.require(principal), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(membership));
  }

  @PostMapping(
      value = "/api/v1/me/memberships/checkout",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<ApiResponse<MembershipResponse>> checkoutWithReceipt(
      @RequestParam("name") String name,
      @RequestParam("email") String email,
      @RequestParam(value = "paymentReference", required = false) String paymentReference,
      @RequestParam(value = "planCode", defaultValue = "ANNUAL") MembershipPlanCode planCode,
      @RequestPart("receiptImage") MultipartFile receiptImage,
      Principal principal) {
    var request = new MembershipReceiptCheckoutRequest(name, email, paymentReference, planCode);
    var membership = memberships.checkoutWithReceipt(
        currentUsers.require(principal), request, receiptImage);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(membership));
  }

  @PostMapping("/api/v1/me/memberships/{id}/mock-payment")
  ApiResponse<MembershipResponse> mockPayment(@PathVariable Long id, Principal principal) {
    if (!mockPaymentEnabled) {
      throw new IllegalArgumentException("الدفع التجريبي معطّل؛ يجب استخدام بوابة الدفع الفعلية");
    }
    return ApiResponse.ok(memberships.mockPay(currentUsers.require(principal), id));
  }

  @GetMapping("/api/v1/me/memberships/current")
  ApiResponse<MembershipResponse> current(Principal principal) {
    return ApiResponse.ok(memberships.current(currentUsers.require(principal)).orElse(null));
  }

  @GetMapping("/api/v1/me/memberships")
  ApiResponse<Page<MembershipResponse>> history(
      Principal principal,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ApiResponse.ok(memberships.history(currentUsers.require(principal), pageable));
  }

  @GetMapping("/api/v1/me/memberships/{id}/receipt")
  ResponseEntity<Resource> myReceipt(@PathVariable Long id, Principal principal) {
    return receiptResponse(
        memberships.receiptForUser(currentUsers.require(principal), id));
  }

  @GetMapping("/api/v1/admin/memberships")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ApiResponse<Page<MembershipResponse>> adminList(
      @RequestParam(required = false) MembershipStatus status,
      @PageableDefault(size = 25, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ApiResponse.ok(memberships.adminList(status, pageable));
  }

  @GetMapping("/api/v1/admin/memberships/{id}/receipt")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ResponseEntity<Resource> receiptForAdmin(@PathVariable Long id) {
    return receiptResponse(memberships.receiptForAdmin(id));
  }

  @PostMapping("/api/v1/admin/memberships/{id}/approve")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ApiResponse<MembershipResponse> approve(@PathVariable Long id, Principal principal) {
    return ApiResponse.ok(memberships.approve(id, principal.getName()));
  }

  @PostMapping("/api/v1/admin/memberships/{id}/reject")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ApiResponse<MembershipResponse> reject(
      @PathVariable Long id,
      @Valid @RequestBody(required = false) MembershipRejectionRequest request,
      Principal principal) {
    return ApiResponse.ok(memberships.reject(id, request, principal.getName()));
  }

  private static ResponseEntity<Resource> receiptResponse(
      MembershipReceiptStorageService.StoredReceiptResource receipt) {
    var originalName = receipt.originalName() == null || receipt.originalName().isBlank()
        ? "receipt"
        : receipt.originalName();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(receipt.contentType()))
        .contentLength(receipt.sizeBytes())
        .cacheControl(CacheControl.noStore().cachePrivate())
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline()
                .filename(originalName, StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(receipt.resource());
  }
}
