package com.jameati.importing;

import static com.jameati.importing.OfficialOpportunityDtos.ImportPreview;
import static com.jameati.importing.OfficialOpportunityDtos.ImportRunSummary;

import com.jameati.common.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/imports")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminImportController {
  private final OfficialOpportunityImportService service;

  public AdminImportController(OfficialOpportunityImportService service) {
    this.service = service;
  }

  @GetMapping("/official-opportunities/preview")
  ApiResponse<ImportPreview> preview() {
    return ApiResponse.ok(service.preview());
  }

  @PostMapping("/official-opportunities/run")
  ResponseEntity<ApiResponse<ImportRunSummary>> run() {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.run()));
  }

  @GetMapping("/runs")
  ApiResponse<Page<ImportRunSummary>> history(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          org.springframework.data.domain.Pageable pageable) {
    return ApiResponse.ok(service.history(pageable));
  }
}
