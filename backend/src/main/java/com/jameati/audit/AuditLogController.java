package com.jameati.audit;

import static com.jameati.audit.AuditDtos.*;

import com.jameati.common.ApiResponse;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AuditLogController {
  private final AuditLogService service;

  public AuditLogController(AuditLogService service) {
    this.service = service;
  }

  @GetMapping
  ApiResponse<Page<AuditResponse>> list(
      @RequestParam(defaultValue = "") String actor,
      @RequestParam(defaultValue = "") String resource,
      @PageableDefault(size = 30, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ApiResponse.ok(service.list(actor, resource, pageable));
  }
}
