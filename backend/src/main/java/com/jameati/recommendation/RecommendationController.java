package com.jameati.recommendation;

import com.jameati.common.ApiResponse;
import com.jameati.me.CurrentUserService;
import com.jameati.subscription.FeatureAccessService;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class RecommendationController {
  private final CurrentUserService currentUsers;
  private final RecommendationService recommendations;
  private final FeatureAccessService features;

  public RecommendationController(
      CurrentUserService currentUsers,
      RecommendationService recommendations,
      FeatureAccessService features) {
    this.currentUsers = currentUsers;
    this.recommendations = recommendations;
    this.features = features;
  }

  @GetMapping("/recommendations")
  ApiResponse<RecommendationDtos.RecommendationBundle> recommendations(Principal principal) {
    return ApiResponse.ok(recommendations.recommendations(currentUsers.require(principal)));
  }

  @GetMapping("/features")
  ApiResponse<FeatureAccessService.AccessSummary> features(Principal principal) {
    return ApiResponse.ok(features.summary(currentUsers.require(principal)));
  }
}
