package com.jameati.support;

import static com.jameati.support.SupportDtos.*;

import com.jameati.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/public/newsletter/subscriptions")
public class NewsletterController {
  private final SupportService service;

  public NewsletterController(SupportService service) {
    this.service = service;
  }

  @PostMapping
  public ApiResponse<NewsletterResponse> subscribe(
      @Valid @RequestBody NewsletterRequest request) {
    return ApiResponse.ok(service.subscribe(request));
  }

  @DeleteMapping
  public ApiResponse<UnsubscribeResponse> unsubscribe(
      @RequestParam @NotBlank @Email String email) {
    return ApiResponse.ok(service.unsubscribe(email));
  }
}
