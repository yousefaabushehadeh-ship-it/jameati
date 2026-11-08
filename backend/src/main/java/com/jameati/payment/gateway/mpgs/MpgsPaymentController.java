package com.jameati.payment.gateway.mpgs;

import static com.jameati.payment.gateway.mpgs.MpgsPaymentDtos.*;

import com.jameati.common.ApiResponse;
import com.jameati.me.CurrentUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/payments/mpgs")
@ConditionalOnProperty(prefix = "app.payment.mpgs", name = "enabled", havingValue = "true")
public class MpgsPaymentController {
  private final MpgsPaymentService payments;
  private final CurrentUserService currentUsers;
  public MpgsPaymentController(MpgsPaymentService payments, CurrentUserService currentUsers) {
    this.payments = payments;
    this.currentUsers = currentUsers;
  }
  @PostMapping
  ResponseEntity<ApiResponse<PaymentResponse>> create(@Valid @RequestBody CreatePaymentRequest request, Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(payments.create(currentUsers.require(principal), request)));
  }
  @PostMapping("/{paymentId}/initiate-authentication")
  ApiResponse<PaymentResponse> initiate(@PathVariable long paymentId, @Valid @RequestBody SessionRequest request, Principal principal) {
    return ApiResponse.ok(payments.initiate(currentUsers.require(principal), paymentId, request));
  }
  @PostMapping("/{paymentId}/authenticate")
  ApiResponse<PaymentResponse> authenticate(@PathVariable long paymentId, @Valid @RequestBody AuthenticateRequest request, Principal principal) {
    return ApiResponse.ok(payments.authenticate(currentUsers.require(principal), paymentId, request));
  }
  @PostMapping("/{paymentId}/pay")
  ApiResponse<PaymentResponse> pay(@PathVariable long paymentId, Principal principal) {
    return ApiResponse.ok(payments.pay(currentUsers.require(principal), paymentId));
  }
  @GetMapping("/{paymentId}")
  ApiResponse<PaymentResponse> status(@PathVariable long paymentId, Principal principal) {
    return ApiResponse.ok(payments.status(currentUsers.require(principal), paymentId));
  }
}
