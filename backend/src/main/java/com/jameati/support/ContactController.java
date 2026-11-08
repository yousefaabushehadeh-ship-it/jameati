package com.jameati.support;

import static com.jameati.support.SupportDtos.*;

import com.jameati.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/support/contact")
public class ContactController {
  private final SupportService service;

  public ContactController(SupportService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ContactReceipt>> submit(
      @Valid @RequestBody ContactRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.created(service.submitContact(request)));
  }
}
