package com.jameati.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jameati.me.UserProfile;
import com.jameati.opportunity.FundingType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScholarshipRecommendationScorerTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T00:00:00Z"), ZoneOffset.UTC);
  private final ScholarshipRecommendationScorer scorer = new ScholarshipRecommendationScorer(CLOCK);

  @Test
  void hardEligibilityFailuresOverrideSoftMatches() {
    var profile = profile();
    var wrongDegree = scholarship(LocalDate.of(2027, 1, 1), Set.of("MASTER"), Set.of("Jordanian"));
    assertThat(scorer.eligibility(profile, wrongDegree)).satisfies(result -> {
      assertThat(result.eligible()).isFalse();
      assertThat(result.failures()).containsExactly("DEGREE_NOT_ELIGIBLE");
    });

    var wrongNationality = scholarship(LocalDate.of(2027, 1, 1), Set.of("BACHELOR"), Set.of("German"));
    assertThat(scorer.eligibility(profile, wrongNationality).failures())
        .containsExactly("NATIONALITY_NOT_ELIGIBLE");
  }

  @Test
  void calculatesExplainableScholarshipScoreFromDeclaredWeights() {
    var result = scorer.score(profile(),
        scholarship(LocalDate.of(2027, 1, 1), Set.of("BACHELOR"), Set.of("ALL")));

    assertThat(result.matchScore()).isEqualTo(100);
    assertThat(result.matchFactors()).hasSize(6);
    assertThat(result.matchFactors()).extracting(MatchFactor::key)
        .containsExactly("field", "country", "degree", "nationality", "funding", "interests");
    assertThat(result.matchFactors()).allSatisfy(factor ->
        assertThat(factor.score()).isBetween(0, factor.maxScore()));
  }

  @Test
  void rejectsExpiredOpportunityEvenWhenEverythingElseMatches() {
    var expired = scholarship(LocalDate.of(2026, 9, 19), Set.of("BACHELOR"), Set.of("ALL"));
    assertThat(scorer.eligibility(profile(), expired).failures()).containsExactly("EXPIRED");
  }

  private static UserProfile profile() {
    var profile = new UserProfile();
    profile.setDegreeLevel("BACHELOR");
    profile.setNationality("Jordanian");
    profile.setPreferredFunding(FundingType.FULLY_FUNDED);
    profile.setPreferredCountries(Set.of("Germany"));
    profile.setPreferredSpecialities(Set.of("Computer Science"));
    profile.setInterests(Set.of("technology"));
    return profile;
  }

  static RecommendationCandidate scholarship(
      LocalDate deadline, Set<String> degrees, Set<String> nationalities) {
    return new RecommendationCandidate(
        RecommendationType.SCHOLARSHIP, 1L, "scholarship", "Technology Scholarship",
        "University", "منح دراسية", "Germany", deadline, false,
        FundingType.FULLY_FUNDED, Set.of("Computer Science"), nationalities, degrees, Map.of());
  }
}
