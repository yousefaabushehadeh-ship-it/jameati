package com.jameati.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jameati.me.UserProfile;
import com.jameati.onboarding.EmploymentType;
import com.jameati.onboarding.UserGoal;
import com.jameati.onboarding.WorkMode;
import com.jameati.opportunity.FundingType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecommendationRankingServiceTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void excludesIneligibleAndBelowThresholdCandidatesThenRanksDescending() {
    var profile = profile();
    var candidates = mock(RecommendationCandidateService.class);
    var highJob = JobRecommendationScorerTest.job(true, 2);
    var scholarship = ScholarshipRecommendationScorerTest.scholarship(
        LocalDate.of(2027, 1, 1), Set.of("BACHELOR"), Set.of("ALL"));
    var expired = ScholarshipRecommendationScorerTest.scholarship(
        LocalDate.of(2026, 9, 19), Set.of("BACHELOR"), Set.of("ALL"));
    when(candidates.findCandidates(profile)).thenReturn(List.of(scholarship, expired, highJob));
    var service = new RecommendationRankingService(candidates, List.of(
        new ScholarshipRecommendationScorer(CLOCK),
        new OpportunityRecommendationScorer(CLOCK),
        new JobRecommendationScorer()), 55);

    var ranked = service.rank(profile);

    assertThat(ranked).hasSize(2);
    assertThat(ranked).extracting(item -> item.candidate().type())
        .containsExactly(RecommendationType.SCHOLARSHIP, RecommendationType.JOB);
    assertThat(ranked).extracting(ScoredRecommendation::matchScore).isSortedAccordingTo(
        java.util.Comparator.reverseOrder());
  }

  private static UserProfile profile() {
    var profile = new UserProfile();
    profile.setOnboardingCompleted(true);
    profile.setGoals(Set.of(UserGoal.FIND_SCHOLARSHIP, UserGoal.FIND_REMOTE_JOB));
    profile.setDegreeLevel("BACHELOR");
    profile.setNationality("Jordanian");
    profile.setPreferredFunding(FundingType.FULLY_FUNDED);
    profile.setPreferredCountries(Set.of("Germany"));
    profile.setPreferredSpecialities(Set.of("Computer Science", "Engineering"));
    profile.setInterests(Set.of("technology"));
    profile.setSkills(Set.of("Java"));
    profile.setPreferredJobTitles(Set.of("Backend Developer"));
    profile.setPreferredWorkMode(WorkMode.REMOTE);
    profile.setPreferredEmploymentType(EmploymentType.FULL_TIME);
    profile.setYearsOfExperience(3);
    profile.setLanguages(Set.of("English"));
    return profile;
  }
}
