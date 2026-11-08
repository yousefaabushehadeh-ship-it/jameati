package com.jameati.ai;

import com.jameati.common.ApiResponse;
import com.jameati.me.CurrentUserService;
import com.jameati.recommendation.RecommendationDtos.RecommendationBundle;
import com.jameati.recommendation.RecommendationService;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/ai-recommendations")
public class AiRecommendationController {
  private final CurrentUserService currentUsers;
  private final RecommendationService recommendations;

  public AiRecommendationController(
      CurrentUserService currentUsers, RecommendationService recommendations) {
    this.currentUsers = currentUsers;
    this.recommendations = recommendations;
  }

  @GetMapping
  ResponseEntity<ApiResponse<RecommendationBundle>> local(Principal principal) {
    return noStore(recommendations.recommendations(currentUsers.require(principal)));
  }

  private static ResponseEntity<ApiResponse<RecommendationBundle>> noStore(
      RecommendationBundle value) {
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .body(ApiResponse.ok(value));
  }
}
