package com.jameati.catalog;

import static com.jameati.catalog.CatalogAdminDtos.*;

import com.jameati.admin.AdminAuthorization;
import com.jameati.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/catalog/{type}")
@PreAuthorize("hasAnyRole('DATA_ENTRY','EDITOR','ADMIN','SUPER_ADMIN')")
public class CatalogAdminController {
  private final CatalogAdminService service;

  public CatalogAdminController(CatalogAdminService service) {
    this.service = service;
  }

  @GetMapping
  ApiResponse<Page<ItemResponse>> list(
      @PathVariable CatalogType type,
      @RequestParam(defaultValue = "") String q,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ApiResponse.ok(service.list(type, q, pageable));
  }

  @GetMapping("/{id}")
  ApiResponse<ItemResponse> get(@PathVariable CatalogType type, @PathVariable Long id) {
    return ApiResponse.ok(service.get(type, id));
  }

  @PostMapping
  ResponseEntity<ApiResponse<ItemResponse>> create(
      @PathVariable CatalogType type,
      @Valid @RequestBody UpsertRequest request,
      Authentication authentication) {
    var result = service.create(type, request, AdminAuthorization.isDataEntryOnly(authentication));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result));
  }

  @PutMapping("/{id}")
  ApiResponse<ItemResponse> update(
      @PathVariable CatalogType type,
      @PathVariable Long id,
      @Valid @RequestBody UpsertRequest request,
      Authentication authentication) {
    return ApiResponse.ok(service.update(type, id, request, AdminAuthorization.isDataEntryOnly(authentication)));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ResponseEntity<Void> delete(@PathVariable CatalogType type, @PathVariable Long id) {
    service.delete(type, id);
    return ResponseEntity.noContent().build();
  }
}
