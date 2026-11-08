package com.jameati.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jameati.me.UserProfile;
import com.jameati.onboarding.EmploymentType;
import com.jameati.onboarding.UserGoal;
import com.jameati.onboarding.WorkMode;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JobRecommendationScorerTest {
  private final JobRecommendationScorer scorer = new JobRecommendationScorer();

  @Test
  void scoresJobUsingJobSpecificWeights() {
    var result = scorer.score(profile(), job(true, 2));

    assertThat(result.matchScore()).isEqualTo(100);
    assertThat(result.matchFactors()).extracting(MatchFactor::maxScore)
        .containsExactly(30, 20, 15, 10, 10, 10, 5);
  }

  @Test
  void remoteOnlyGoalRejectsOnSiteJob() {
    assertThat(scorer.eligibility(profile(), job(false, 2)).failures())
        .containsExactly("REMOTE_REQUIRED");
  }

  @Test
  void insufficientExperienceIsHardFailure() {
    assertThat(scorer.eligibility(profile(), job(true, 5)).failures())
        .containsExactly("INSUFFICIENT_EXPERIENCE");
  }

  private static UserProfile profile() {
    var profile = new UserProfile();
    profile.setGoals(Set.of(UserGoal.FIND_REMOTE_JOB));
    profile.setSkills(Set.of("Java"));
    profile.setPreferredSpecialities(Set.of("Engineering"));
    profile.setPreferredJobTitles(Set.of("Backend Developer"));
    profile.setPreferredCountries(Set.of("Germany"));
    profile.setPreferredWorkMode(WorkMode.REMOTE);
    profile.setPreferredEmploymentType(EmploymentType.FULL_TIME);
    profile.setYearsOfExperience(3);
    profile.setLanguages(Set.of("English"));
    return profile;
  }

  static RecommendationCandidate job(boolean remote, int minimumExperience) {
    return new RecommendationCandidate(
        RecommendationType.JOB, 2L, "backend-developer", "Senior Backend Developer Java",
        "Company", "Engineering", "Germany", null, false, null,
        Set.of(), Set.of(), Set.of(), Map.of(
            "remote", remote,
            "tags", java.util.List.of("Java", "Engineering"),
            "requiredSkills", java.util.List.of("Java"),
            "minimumExperienceYears", minimumExperience,
            "jobTypes", java.util.List.of("FULL_TIME"),
            "languages", java.util.List.of("English")));
  }
}
