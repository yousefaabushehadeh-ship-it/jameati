package com.jameati.notification;

import static com.jameati.notification.AdminNotificationDtos.DeliveryRequest;
import static com.jameati.notification.AdminNotificationDtos.DeliveryResponse;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminNotificationController {
  private final CurrentUserService currentUsers;
  private final AdminNotificationService service;

  public AdminNotificationController(
      CurrentUserService currentUsers, AdminNotificationService service) {
    this.currentUsers = currentUsers;
    this.service = service;
  }

  @GetMapping
  ApiResponse<Page<DeliveryResponse>> history(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ApiResponse.ok(service.history(pageable));
  }

  @PostMapping
  ResponseEntity<ApiResponse<DeliveryResponse>> create(
      Principal principal, @Valid @RequestBody DeliveryRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.created(service.create(currentUsers.require(principal), request)));
  }
}
