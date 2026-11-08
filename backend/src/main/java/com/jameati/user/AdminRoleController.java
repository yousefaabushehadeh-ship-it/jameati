package com.jameati.user;

import com.jameati.common.ApiResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/roles")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminRoleController {
  @GetMapping
  ApiResponse<List<RoleName>> roles() {
    return ApiResponse.ok(List.of(RoleName.values()));
  }
}
