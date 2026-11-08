package com.jameati.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.jameati.common.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * استقبال فرص من أدوات خارجية (مثل مساعد واتساب "Tab").
 * محمي بمفتاح سري في الهيدر X-API-Key (متغير البيئة INTEGRATION_API_KEY).
 * كل فرصة بتنحفظ كمسودة (غير منشورة) لحد ما يوافق عليها الأدمن.
 */
@RestController
@RequestMapping("/api/v1/integrations/opportunities")
public class OpportunityIntakeController {
  private final OpportunityIntakeService service;
  private final byte[] apiKey;

  public OpportunityIntakeController(
      OpportunityIntakeService service, @Value("${app.integration.api-key:}") String apiKey) {
    this.service = service;
    this.apiKey = apiKey == null ? new byte[0] : apiKey.trim().getBytes(StandardCharsets.UTF_8);
  }

  @PostMapping
  public ResponseEntity<ApiResponse<?>> intake(
      @RequestHeader(value = "X-API-Key", required = false) String key, @RequestBody JsonNode body) {
    if (apiKey.length == 0) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(ApiResponse.error("استقبال الفرص غير مفعّل على السيرفر (INTEGRATION_API_KEY فاضي)"));
    }
    if (key == null || !MessageDigest.isEqual(apiKey, key.trim().getBytes(StandardCharsets.UTF_8))) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("مفتاح API غير صحيح"));
    }
    var result = service.intake(body);
    var status = result.created() > 0 ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(new ApiResponse<>(true, result, result.message(), java.time.Instant.now()));
  }
}
