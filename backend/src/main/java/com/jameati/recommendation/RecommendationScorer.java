package com.jameati.recommendation;

import com.jameati.me.UserProfile;

public interface RecommendationScorer {
  boolean supports(RecommendationType type);
  EligibilityResult eligibility(UserProfile profile, RecommendationCandidate candidate);
  ScoredRecommendation score(UserProfile profile, RecommendationCandidate candidate);
}
