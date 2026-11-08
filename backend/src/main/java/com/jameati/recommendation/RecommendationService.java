package com.jameati.recommendation;

import static com.jameati.recommendation.RecommendationDtos.*;

import com.jameati.ai.AiRecommendationExplanationService;
import com.jameati.me.UserProfileRepository;
import com.jameati.subscription.FeatureAccessService;
import com.jameati.user.User;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecommendationService {
  private static final int AI_EXPLANATION_LIMIT = 3;
  private final UserProfileRepository profiles;
  private final RecommendationRankingService ranking;
  private final FeatureAccessService features;
  private final AiRecommendationExplanationService explanations;

  public RecommendationService(
      UserProfileRepository profiles,
      RecommendationRankingService ranking,
      FeatureAccessService features,
      AiRecommendationExplanationService explanations) {
    this.profiles = profiles;
    this.ranking = ranking;
    this.features = features;
    this.explanations = explanations;
  }

  public RecommendationBundle recommendations(User user) {
    var profile = profiles.findByUserIdAndDeletedFalse(user.getId())
        .orElseThrow(() -> new IllegalStateException("ملف المستخدم غير موجود"));
    var ranked = ranking.rank(profile);
    var access = features.summary(user);
    var limit = features.getRecommendationLimit(user);
    var visibleRanked = ranked.stream().limit(limit).toList();
    var visible = java.util.stream.IntStream.range(0, visibleRanked.size())
        .mapToObj(index -> item(profile, visibleRanked.get(index), index < AI_EXPLANATION_LIMIT))
        .toList();
    var locked = Math.max(0, ranked.size() - visible.size());
    return new RecommendationBundle(
        profile.isOnboardingCompleted(), profile.getProfileCompletionPercentage(), access,
        visible, ranked.size(), visible.size(), locked, locked > 0);
  }

  private RecommendationItem item(
      com.jameati.me.UserProfile profile, ScoredRecommendation scored, boolean aiSelected) {
    var candidate = scored.candidate();
    var route = candidate.type() == RecommendationType.JOB
        ? "/jobs/" + candidate.slug() : "/opportunities/" + candidate.slug();
    return new RecommendationItem(
        candidate.type(), candidate.id(), candidate.slug(), route, candidate.title(),
        candidate.organization(), candidate.category(), candidate.country(), candidate.deadline(),
        candidate.alwaysOpen(), candidate.funding(), scored.matchScore(),
        List.copyOf(scored.matchFactors()), explanation(profile, scored, aiSelected));
  }

  private RecommendationExplanation explanation(
      com.jameati.me.UserProfile profile, ScoredRecommendation scored, boolean aiSelected) {
    if (!aiSelected) return explanations.deterministic(scored);
    try {
      return explanations.explain(profile, scored);
    } catch (RuntimeException unavailable) {
      return explanations.deterministic(scored);
    }
  }
}
