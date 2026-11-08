package com.jameati.settings;

import com.jameati.common.ApiResponse;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class SiteSettingController {
  private final SiteSettingService service;

  public SiteSettingController(SiteSettingService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/public/settings")
  ApiResponse<Map<String, Object>> publicSettings(@RequestParam String namespace) {
    return ApiResponse.ok(service.publicSettings(namespace));
  }

  @GetMapping("/api/v1/admin/settings/{namespace}")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ApiResponse<Map<String, Object>> adminSettings(@PathVariable String namespace) {
    return ApiResponse.ok(service.adminSettings(namespace));
  }

  @PutMapping("/api/v1/admin/settings/{namespace}")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ApiResponse<Map<String, Object>> replace(
      @PathVariable String namespace, @RequestBody Map<String, Object> values) {
    return ApiResponse.ok(service.replace(namespace, values));
  }
}
