package com.jameati.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.me.UserProfile;
import com.jameati.recommendation.MatchFactor;
import com.jameati.recommendation.RecommendationCandidate;
import com.jameati.recommendation.RecommendationExplanation;
import com.jameati.recommendation.RecommendationType;
import com.jameati.recommendation.ScoredRecommendation;
import com.jameati.user.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiRecommendationExplanationServiceTest {
  private RecommendationExplanationCacheRepository cache;
  private OpenAiClient openAi;
  private AiRateLimiter limiter;
  private AiRecommendationExplanationService service;
  private UserProfile profile;

  @BeforeEach
  void setUp() {
    cache = mock(RecommendationExplanationCacheRepository.class);
    openAi = mock(OpenAiClient.class);
    limiter = mock(AiRateLimiter.class);
    service = new AiRecommendationExplanationService(cache, openAi, limiter, new ObjectMapper());
    var user = new User("private@example.com", "secret", "Private Name");
    user.setId(42L);
    profile = new UserProfile();
    profile.setUser(user);
    profile.setPrimarySpeciality("هندسة البرمجيات");
    profile.setDegreeLevel("بكالوريوس");
    profile.setPreferredCountries(Set.of("ألمانيا"));
    when(cache.findByUserIdAndRecommendationTypeAndCandidateIdAndInputFingerprintAndDeletedFalse(
        any(), any(), any(), any())).thenReturn(Optional.empty());
  }

  @Test
  void sendsOnlySelectedFactsAndMinimumProfileContextAndCachesStructuredResult() {
    when(openAi.explain(anyMap(), anyMap(), anyInt(), anyList())).thenReturn(
        new OpenAiClient.ExplanationResult("مناسبة لتخصصك ووجهتك.",
            List.of("توافق التخصص"), List.of("شرط IELTS مخترع")));

    var result = service.explain(profile, recommendation("فرصة أصلية"));

    assertThat(result.source()).isEqualTo(RecommendationExplanation.Source.OPENAI);
    assertThat(result.warnings()).containsExactly("الدولة ليست ضمن اختياراتك");
    var facts = ArgumentCaptor.forClass(Map.class);
    var context = ArgumentCaptor.forClass(Map.class);
    verify(openAi).explain(facts.capture(), context.capture(), anyInt(), anyList());
    assertThat(facts.getValue()).containsKeys("title", "acceptedDegreeLevels");
    assertThat(context.getValue()).containsKeys("primarySpeciality", "degreeLevel");
    assertThat(context.getValue().toString()).doesNotContain(
        "private@example.com", "secret", "Private Name");
    verify(cache).saveAndFlush(any(RecommendationExplanationCache.class));
  }

  @Test
  void reusesCacheWithoutCallingOpenAi() {
    var cached = new RecommendationExplanationCache();
    cached.setPayloadJson("{\"summary\":\"شرح محفوظ\",\"advantages\":[\"ميزة\"],\"warnings\":[]}");
    when(cache.findByUserIdAndRecommendationTypeAndCandidateIdAndInputFingerprintAndDeletedFalse(
        any(), any(), any(), any())).thenReturn(Optional.of(cached));

    var result = service.explain(profile, recommendation("فرصة أصلية"));

    assertThat(result.source()).isEqualTo(RecommendationExplanation.Source.CACHE);
    assertThat(result.summary()).isEqualTo("شرح محفوظ");
    verify(openAi, never()).explain(anyMap(), anyMap(), anyInt(), anyList());
    verify(limiter, never()).consume(any(), org.mockito.ArgumentMatchers.anyBoolean());
  }

  @Test
  void changedProfileOrOpportunityProducesANewCacheFingerprint() {
    when(openAi.explain(anyMap(), anyMap(), anyInt(), anyList())).thenReturn(
        new OpenAiClient.ExplanationResult("شرح آمن", List.of(), List.of()));
    service.explain(profile, recommendation("الفرصة الأولى"));
    profile.setPrimarySpeciality("علم البيانات");
    service.explain(profile, recommendation("الفرصة الأولى"));
    service.explain(profile, recommendation("الفرصة المعدلة"));

    var saved = ArgumentCaptor.forClass(RecommendationExplanationCache.class);
    verify(cache, org.mockito.Mockito.times(3)).saveAndFlush(saved.capture());
    assertThat(saved.getAllValues()).extracting(RecommendationExplanationCache::getInputFingerprint)
        .doesNotHaveDuplicates();
  }

  @Test
  void fallsBackDeterministicallyWhenOpenAiFailsAndDoesNotCacheFailure() {
    when(openAi.explain(anyMap(), anyMap(), anyInt(), anyList()))
        .thenThrow(new AiException(503, "provider_down"));

    var result = service.explain(profile, recommendation("فرصة أصلية"));

    assertThat(result.source()).isEqualTo(RecommendationExplanation.Source.FALLBACK);
    assertThat(result.advantages()).contains("توافق التخصص");
    assertThat(result.warnings()).contains("الدولة ليست ضمن اختياراتك");
    verify(cache, never()).saveAndFlush(any());
  }

  private static ScoredRecommendation recommendation(String title) {
    var candidate = new RecommendationCandidate(
        RecommendationType.SCHOLARSHIP, 9L, "scholarship-9", title, "جامعة", "منحة",
        "ألمانيا", LocalDate.of(2027, 6, 1), false, null,
        Set.of("هندسة البرمجيات"), Set.of("ALL"), Set.of("بكالوريوس"), Map.of());
    return new ScoredRecommendation(candidate, 88, List.of(
        new MatchFactor("major", 25, 25, true, "توافق التخصص"),
        new MatchFactor("country", 0, 10, false, "الدولة ليست ضمن اختياراتك")));
  }
}
