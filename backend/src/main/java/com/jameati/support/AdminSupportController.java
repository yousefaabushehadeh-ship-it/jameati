package com.jameati.support;

import static com.jameati.support.SupportDtos.*;

import com.jameati.common.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/support")
public class AdminSupportController {
  private final SupportService service;

  public AdminSupportController(SupportService service) {
    this.service = service;
  }

  @GetMapping("/contact-messages")
  public ApiResponse<Page<ContactAdminResponse>> contacts(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ApiResponse.ok(service.contacts(pageable));
  }

  @GetMapping("/newsletter-subscribers")
  public ApiResponse<Page<NewsletterAdminResponse>> subscribers(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ApiResponse.ok(service.subscribers(pageable));
  }
}
