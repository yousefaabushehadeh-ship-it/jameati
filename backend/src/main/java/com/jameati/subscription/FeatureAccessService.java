package com.jameati.subscription;

import com.jameati.user.User;
import org.springframework.stereotype.Service;

@Service
public class FeatureAccessService {
  public static final int FREE_RECOMMENDATION_LIMIT = 3;
  private final ContentAccessService contentAccess;

  public FeatureAccessService(ContentAccessService contentAccess) {
    this.contentAccess = contentAccess;
  }

  public boolean hasAccess(User user, Feature feature) {
    if (feature == Feature.PERSONALIZED_RECOMMENDATIONS) return true;
    return contentAccess.resolve(user).fullAccess();
  }

  public int getRecommendationLimit(User user) {
    return hasAccess(user, Feature.UNLIMITED_RECOMMENDATIONS)
        ? Integer.MAX_VALUE : FREE_RECOMMENDATION_LIMIT;
  }

  public AccessSummary summary(User user) {
    var unlimited = hasAccess(user, Feature.UNLIMITED_RECOMMENDATIONS);
    return new AccessSummary(
        unlimited ? "PREMIUM" : "FREE",
        unlimited ? null : FREE_RECOMMENDATION_LIMIT,
        unlimited,
        hasAccess(user, Feature.ADVANCED_MATCH_DETAILS),
        hasAccess(user, Feature.OPPORTUNITY_COMPARISON),
        hasAccess(user, Feature.RECOMMENDATION_ALERTS));
  }

  public record AccessSummary(
      String plan,
      Integer recommendationLimit,
      boolean unlimitedRecommendations,
      boolean advancedMatchDetails,
      boolean opportunityComparison,
      boolean recommendationAlerts) {}
}
