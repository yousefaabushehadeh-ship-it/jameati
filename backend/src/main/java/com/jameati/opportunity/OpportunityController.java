package com.jameati.opportunity;

import static com.jameati.opportunity.OpportunityDtos.*;
import com.jameati.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class OpportunityController {
  private final OpportunityService service; private final OpportunityAdminPolicyService adminPolicy;
  public OpportunityController(OpportunityService service,OpportunityAdminPolicyService adminPolicy){this.service=service;this.adminPolicy=adminPolicy;}
  @GetMapping("/api/v1/public/opportunities") ApiResponse<Page<OpportunityResponse>> search(@ModelAttribute Filter f,@PageableDefault(size=12,sort="createdAt",direction=Sort.Direction.DESC) Pageable p,Principal principal){return ApiResponse.ok(service.search(f,p,principal));}
  @GetMapping("/api/v1/public/opportunities/featured") ApiResponse<List<OpportunityResponse>> featured(@RequestParam(defaultValue="6") int limit,Principal principal){return ApiResponse.ok(service.featured(limit,principal));}
  @GetMapping("/api/v1/public/opportunities/homepage") ApiResponse<Page<OpportunityResponse>> homepage(@RequestParam(defaultValue="4") int limit,Principal principal){return ApiResponse.ok(service.homepage(limit,principal));}
  @GetMapping("/api/v1/public/opportunities/{slug}") ApiResponse<OpportunityResponse> detail(@PathVariable String slug,Principal principal){return ApiResponse.ok(service.detail(slug,principal));}
  @GetMapping("/api/v1/admin/opportunities") @PreAuthorize("hasAnyRole('DATA_ENTRY','EDITOR','ADMIN','SUPER_ADMIN')") ApiResponse<Page<OpportunityResponse>> admin(@PageableDefault(size=20) Pageable p){return ApiResponse.ok(service.adminList(p));}
  @GetMapping("/api/v1/admin/opportunities/{id}") @PreAuthorize("hasAnyRole('DATA_ENTRY','EDITOR','ADMIN','SUPER_ADMIN')") ApiResponse<OpportunityResponse> adminDetail(@PathVariable Long id){return ApiResponse.ok(adminPolicy.detail(id));}
  @PostMapping("/api/v1/admin/opportunities") @PreAuthorize("hasAnyRole('DATA_ENTRY','EDITOR','ADMIN','SUPER_ADMIN')") ResponseEntity<ApiResponse<OpportunityResponse>> create(@Valid @RequestBody UpsertRequest r,Authentication authentication){return ResponseEntity.status(201).body(ApiResponse.created(adminPolicy.create(r,authentication)));}
  @PutMapping("/api/v1/admin/opportunities/{id}") @PreAuthorize("hasAnyRole('DATA_ENTRY','EDITOR','ADMIN','SUPER_ADMIN')") ApiResponse<OpportunityResponse> update(@PathVariable Long id,@Valid @RequestBody UpsertRequest r,Authentication authentication){return ApiResponse.ok(adminPolicy.update(id,r,authentication));}
  @DeleteMapping("/api/v1/admin/opportunities/{id}") @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')") ResponseEntity<Void> delete(@PathVariable Long id){service.delete(id);return ResponseEntity.noContent().build();}
}
