package com.jameati.onboarding;

import static com.jameati.onboarding.OnboardingDtos.*;

import com.jameati.common.ApiResponse;
import com.jameati.me.CurrentUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/onboarding")
public class OnboardingController {
  private final CurrentUserService currentUsers;
  private final OnboardingService onboarding;

  public OnboardingController(CurrentUserService currentUsers, OnboardingService onboarding) {
    this.currentUsers = currentUsers;
    this.onboarding = onboarding;
  }

  @GetMapping
  ApiResponse<OnboardingResponse> get(Principal principal) {
    return ApiResponse.ok(onboarding.get(currentUsers.require(principal)));
  }

  @PutMapping
  ApiResponse<OnboardingResponse> update(
      Principal principal, @Valid @RequestBody OnboardingUpdateRequest request) {
    return ApiResponse.ok(onboarding.update(currentUsers.require(principal), request));
  }

  @PostMapping("/complete")
  ApiResponse<OnboardingResponse> complete(Principal principal) {
    return ApiResponse.ok(onboarding.complete(currentUsers.require(principal)));
  }
}
