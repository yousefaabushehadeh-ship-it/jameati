package com.jameati.recommendation;

import com.jameati.me.UserProfile;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RecommendationRankingService {
  private final RecommendationCandidateService candidates;
  private final List<RecommendationScorer> scorers;
  private final int minimumScore;

  public RecommendationRankingService(
      RecommendationCandidateService candidates,
      List<RecommendationScorer> scorers,
      @Value("${app.recommendation.minimum-score:55}") int minimumScore) {
    this.candidates = candidates;
    this.scorers = List.copyOf(scorers);
    this.minimumScore = Math.max(0, Math.min(minimumScore, 100));
  }

  public List<ScoredRecommendation> rank(UserProfile profile) {
    if (profile == null || !profile.isOnboardingCompleted()) return List.of();
    return rank(profile, candidates.findCandidates(profile));
  }

  List<ScoredRecommendation> rank(
      UserProfile profile, List<RecommendationCandidate> recommendationCandidates) {
    return recommendationCandidates.stream()
        .map(candidate -> new ScoringTarget(candidate, scorer(candidate.type())))
        .filter(target -> target.scorer().eligibility(profile, target.candidate()).eligible())
        .map(target -> target.scorer().score(profile, target.candidate()))
        .filter(result -> result.matchScore() >= minimumScore)
        .sorted(Comparator.comparingInt(ScoredRecommendation::matchScore).reversed()
            .thenComparing(result -> result.candidate().deadline(),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(result -> result.candidate().id()))
        .toList();
  }

  private RecommendationScorer scorer(RecommendationType type) {
    return scorers.stream().filter(candidate -> candidate.supports(type)).findFirst()
        .orElseThrow(() -> new IllegalStateException("No recommendation scorer for " + type));
  }

  private record ScoringTarget(
      RecommendationCandidate candidate, RecommendationScorer scorer) {}
}
