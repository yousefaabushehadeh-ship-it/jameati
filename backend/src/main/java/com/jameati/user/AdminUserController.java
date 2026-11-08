package com.jameati.user;

import static com.jameati.user.AdminUserDtos.*;

import com.jameati.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminUserController {
  private final AdminUserService service;

  public AdminUserController(AdminUserService service) {
    this.service = service;
  }

  @GetMapping
  ApiResponse<Page<UserResponse>> list(
      @RequestParam(defaultValue = "") String q,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ApiResponse.ok(service.list(q, pageable));
  }

  @PatchMapping("/{id}/status")
  ApiResponse<UserResponse> status(
      @PathVariable Long id, @Valid @RequestBody StatusRequest request, Principal principal) {
    return ApiResponse.ok(service.status(id, request, principal.getName()));
  }

  @PutMapping("/{id}/roles")
  ApiResponse<UserResponse> roles(
      @PathVariable Long id, @Valid @RequestBody RolesRequest request, Principal principal) {
    return ApiResponse.ok(service.roles(id, request, principal.getName()));
  }
}
