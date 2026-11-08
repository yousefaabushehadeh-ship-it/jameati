package com.jameati.ai;

import com.jameati.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiStatusController {
  private final OpenAiConfig config;

  public AiStatusController(OpenAiConfig config) {
    this.config = config;
  }

  public record Status(int version, boolean enabled) {}

  @GetMapping("/api/v1/public/ai/status")
  public ResponseEntity<ApiResponse<Status>> status() {
    boolean configured = config.getApiKey() != null && !config.getApiKey().isBlank();
    return ResponseEntity.ok().header("Cache-Control", "no-store")
        .body(ApiResponse.ok(new Status(1, configured)));
  }
}
