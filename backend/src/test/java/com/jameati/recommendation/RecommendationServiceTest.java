package com.jameati.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jameati.me.UserProfile;
import com.jameati.me.UserProfileRepository;
import com.jameati.ai.AiRecommendationExplanationService;
import com.jameati.subscription.ContentAccessService;
import com.jameati.subscription.FeatureAccessService;
import com.jameati.user.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecommendationServiceTest {
  private User user;
  private UserProfile profile;
  private UserProfileRepository profiles;
  private RecommendationRankingService ranking;
  private ContentAccessService contentAccess;
  private AiRecommendationExplanationService explanations;

  @BeforeEach
  void setUp() {
    user = new User("student@example.com", "hash", "Student");
    user.setId(7L);
    profile = new UserProfile();
    profile.setUser(user);
    profile.setOnboardingCompleted(true);
    profile.setProfileCompletionPercentage(80);
    profiles = mock(UserProfileRepository.class);
    ranking = mock(RecommendationRankingService.class);
    contentAccess = mock(ContentAccessService.class);
    explanations = mock(AiRecommendationExplanationService.class);
    when(profiles.findByUserIdAndDeletedFalse(7L)).thenReturn(Optional.of(profile));
    when(ranking.rank(profile)).thenReturn(List.of(
        scored(1L, 99), scored(2L, 95), scored(3L, 90), scored(4L, 85), scored(5L, 80)));
    when(explanations.explain(org.mockito.ArgumentMatchers.eq(profile), any()))
        .thenReturn(new RecommendationExplanation("شرح", List.of(), List.of(),
            RecommendationExplanation.Source.OPENAI));
    when(explanations.deterministic(any()))
        .thenReturn(new RecommendationExplanation("بديل", List.of(), List.of(),
            RecommendationExplanation.Source.FALLBACK));
  }

  @Test
  void freeUserReceivesOnlyTopThreeAndNoLockedCandidateDetails() {
    when(contentAccess.resolve(user)).thenReturn(
        new ContentAccessService.AccessContext(true, false, null, null));
    var service = service();

    var result = service.recommendations(user);

    assertThat(result.subscription().plan()).isEqualTo("FREE");
    assertThat(result.totalMatches()).isEqualTo(5);
    assertThat(result.visibleMatches()).isEqualTo(3);
    assertThat(result.lockedMatches()).isEqualTo(2);
    assertThat(result.upgradeRequired()).isTrue();
    assertThat(result.recommendations()).extracting(item -> item.id())
        .containsExactly(1L, 2L, 3L)
        .doesNotContain(4L, 5L);
  }

  @Test
  void premiumUserReceivesEveryRankedRecommendation() {
    when(contentAccess.resolve(user)).thenReturn(
        new ContentAccessService.AccessContext(true, true, null, null));
    var result = service().recommendations(user);

    assertThat(result.subscription().plan()).isEqualTo("PREMIUM");
    assertThat(result.subscription().unlimitedRecommendations()).isTrue();
    assertThat(result.visibleMatches()).isEqualTo(5);
    assertThat(result.lockedMatches()).isZero();
    assertThat(result.recommendations()).hasSize(5);
    verify(explanations, times(3)).explain(org.mockito.ArgumentMatchers.eq(profile), any());
    verify(explanations, times(2)).deterministic(any());
  }

  private RecommendationService service() {
    return new RecommendationService(
        profiles, ranking, new FeatureAccessService(contentAccess), explanations);
  }

  private static ScoredRecommendation scored(Long id, int score) {
    var candidate = new RecommendationCandidate(
        RecommendationType.SCHOLARSHIP, id, "item-" + id, "Item " + id, "Organization",
        "منح دراسية", "Germany", LocalDate.of(2027, 1, 1), false, null,
        Set.of(), Set.of(), Set.of(), Map.of());
    return new ScoredRecommendation(candidate, score,
        List.of(new MatchFactor("field", 25, 25, true, "توافق المجال")));
  }
}
