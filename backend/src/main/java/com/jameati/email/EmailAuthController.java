package com.jameati.email;

import static com.jameati.email.EmailAccountDtos.*;

import com.jameati.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class EmailAuthController {
  private final EmailAccountService service;

  public EmailAuthController(EmailAccountService service) {
    this.service = service;
  }

  @PostMapping("/forgot-password")
  ApiResponse<ActionMessage> forgotPassword(@Valid @RequestBody EmailRequest request) {
    return ApiResponse.ok(service.forgotPassword(request.email()));
  }

  @PostMapping("/reset-password")
  ApiResponse<ActionMessage> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    return ApiResponse.ok(service.resetPassword(request.token(), request.password()));
  }

  @PostMapping("/verify-email")
  ApiResponse<ActionMessage> verifyEmail(@Valid @RequestBody TokenRequest request) {
    return ApiResponse.ok(service.verifyEmail(request.token()));
  }

  @PostMapping("/resend-verification")
  ApiResponse<ActionMessage> resendVerification(@Valid @RequestBody EmailRequest request) {
    return ApiResponse.ok(service.resendVerification(request.email()));
  }
}
