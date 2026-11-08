package com.jameati.recommendation;

import com.jameati.me.UserProfile;
import java.time.LocalDate;

final class OpportunityEligibility {
  private OpportunityEligibility() {}

  static EligibilityResult evaluate(
      UserProfile profile, RecommendationCandidate candidate, LocalDate today) {
    if (!candidate.alwaysOpen() && candidate.deadline() != null
        && candidate.deadline().isBefore(today)) {
      return EligibilityResult.failed("EXPIRED");
    }
    if (!candidate.nationalities().isEmpty()
        && !ScoringSupport.containsIgnoreCase(candidate.nationalities(), "ALL")
        && profile.getNationality() != null
        && !ScoringSupport.containsIgnoreCase(candidate.nationalities(), profile.getNationality())) {
      return EligibilityResult.failed("NATIONALITY_NOT_ELIGIBLE");
    }
    if (!candidate.degreeLevels().isEmpty()
        && profile.getDegreeLevel() != null
        && !ScoringSupport.containsIgnoreCase(candidate.degreeLevels(), profile.getDegreeLevel())) {
      return EligibilityResult.failed("DEGREE_NOT_ELIGIBLE");
    }
    return EligibilityResult.passed();
  }
}
