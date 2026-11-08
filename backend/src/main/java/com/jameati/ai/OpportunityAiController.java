package com.jameati.ai;

import com.jameati.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class OpportunityAiController {
  private final OpportunityAiService service;
  public OpportunityAiController(OpportunityAiService service) { this.service=service; }
  @PostMapping("/api/v1/public/opportunities/{id}/ai/chat")
  public ResponseEntity<ApiResponse<AiChatResponse>> opportunity(@PathVariable @Positive long id,@Valid @RequestBody AiChatRequest request,Principal principal) {
    return ResponseEntity.ok().header("Cache-Control","no-store").body(ApiResponse.ok(service.chat(id,false,request,principal)));
  }
  @PostMapping("/api/v1/public/catalog/JOB/{id}/ai/chat")
  public ResponseEntity<ApiResponse<AiChatResponse>> job(@PathVariable @Positive long id,@Valid @RequestBody AiChatRequest request,Principal principal) {
    return ResponseEntity.ok().header("Cache-Control","no-store").body(ApiResponse.ok(service.chat(id,true,request,principal)));
  }
  @ExceptionHandler(AiException.class)
  public ResponseEntity<ApiResponse<Void>> error(AiException error) {
    var builder=ResponseEntity.status(error.status).header("Cache-Control","no-store");
    if(error.retryAfter>0) builder.header("Retry-After",Long.toString(error.retryAfter));
    return builder.body(ApiResponse.error(error.status==429 ? "وصلت إلى حد الرسائل المسموح، يرجى المحاولة لاحقًا."
        : error.status==403 ? "يلزم تفعيل العضوية للوصول إلى تفاصيل هذه الفرصة."
        : "المساعد الذكي غير متاح حاليًا، يرجى المحاولة مرة أخرى بعد قليل."));
  }
}
