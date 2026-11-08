package com.jameati.recommendation;

import com.jameati.opportunity.FundingType;
import com.jameati.subscription.FeatureAccessService.AccessSummary;
import java.time.LocalDate;
import java.util.List;

public final class RecommendationDtos {
  private RecommendationDtos() {}

  public record RecommendationItem(
      RecommendationType type,
      Long id,
      String slug,
      String route,
      String title,
      String organization,
      String category,
      String country,
      LocalDate deadline,
      boolean alwaysOpen,
      FundingType funding,
      int matchScore,
      List<MatchFactor> matchFactors,
      RecommendationExplanation explanation) {}

  public record RecommendationBundle(
      boolean profileCompleted,
      int profileCompletionPercentage,
      AccessSummary subscription,
      List<RecommendationItem> recommendations,
      int totalMatches,
      int visibleMatches,
      int lockedMatches,
      boolean upgradeRequired) {}
}
