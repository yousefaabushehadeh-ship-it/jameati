package com.jameati.email;

import static com.jameati.email.NewsletterCampaignDtos.*;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/newsletter/campaigns")
public class AdminNewsletterCampaignController {
  private final CurrentUserService currentUsers;
  private final NewsletterCampaignService service;

  public AdminNewsletterCampaignController(
      CurrentUserService currentUsers, NewsletterCampaignService service) {
    this.currentUsers = currentUsers;
    this.service = service;
  }

  @GetMapping
  ApiResponse<Page<CampaignResponse>> history(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ApiResponse.ok(service.history(pageable));
  }

  @PostMapping
  ResponseEntity<ApiResponse<CampaignResponse>> create(
      Principal principal, @Valid @RequestBody CampaignRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.created(
                service.create(currentUsers.require(principal), request)));
  }
}
