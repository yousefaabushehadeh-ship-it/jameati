package com.jameati.media;

import static com.jameati.media.MediaDtos.*;

import com.jameati.common.ApiResponse;
import java.security.Principal;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MediaController {
  private final MediaStorageService service;

  public MediaController(MediaStorageService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/admin/media")
  @PreAuthorize("hasAnyRole('DATA_ENTRY','EDITOR','ADMIN','SUPER_ADMIN')")
  ApiResponse<Page<MediaResponse>> list(
      @RequestParam(defaultValue = "") String q,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ApiResponse.ok(service.list(q, pageable));
  }

  @PostMapping(value = "/api/v1/admin/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('DATA_ENTRY','EDITOR','ADMIN','SUPER_ADMIN')")
  ResponseEntity<ApiResponse<MediaResponse>> upload(@RequestPart("file") MultipartFile file, Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.created(service.upload(file, principal.getName())));
  }

  @DeleteMapping("/api/v1/admin/media/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
  ResponseEntity<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/v1/public/media/{storedName}")
  ResponseEntity<org.springframework.core.io.Resource> read(@PathVariable String storedName) {
    var stored = service.read(storedName);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(stored.contentType()))
        .contentLength(stored.size())
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
        .header("X-Content-Type-Options", "nosniff")
        .body(stored.resource());
  }
}
