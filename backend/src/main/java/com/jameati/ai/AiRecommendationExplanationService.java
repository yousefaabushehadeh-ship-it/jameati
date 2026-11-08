package com.jameati.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.me.UserProfile;
import com.jameati.recommendation.MatchFactor;
import com.jameati.recommendation.RecommendationExplanation;
import com.jameati.recommendation.ScoredRecommendation;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiRecommendationExplanationService {
  private static final Logger log = LoggerFactory.getLogger(AiRecommendationExplanationService.class);
  private static final List<String> SAFE_JOB_ATTRIBUTES = List.of(
      "skills", "workMode", "employmentType", "minimumExperience");

  private final RecommendationExplanationCacheRepository cache;
  private final OpenAiClient openAi;
  private final AiRateLimiter limiter;
  private final ObjectMapper mapper;

  public AiRecommendationExplanationService(
      RecommendationExplanationCacheRepository cache, OpenAiClient openAi,
      AiRateLimiter limiter, ObjectMapper mapper) {
    this.cache = cache;
    this.openAi = openAi;
    this.limiter = limiter;
    this.mapper = mapper;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public RecommendationExplanation explain(UserProfile profile, ScoredRecommendation recommendation) {
    var facts = opportunityFacts(recommendation);
    var profileContext = profileContext(profile, recommendation);
    var factors = factorContext(recommendation.matchFactors());
    var fingerprint = fingerprint(facts, profileContext, recommendation.matchScore(), factors);
    var candidate = recommendation.candidate();
    var existing = cache.findByUserIdAndRecommendationTypeAndCandidateIdAndInputFingerprintAndDeletedFalse(
        profile.getUser().getId(), candidate.type(), candidate.id(), fingerprint);
    if (existing.isPresent()) {
      try {
        var payload = mapper.readValue(existing.get().getPayloadJson(), CachedPayload.class);
        return new RecommendationExplanation(payload.summary(), payload.advantages(), payload.warnings(),
            RecommendationExplanation.Source.CACHE);
      } catch (Exception error) {
        log.warn("Invalid recommendation explanation cache id={}", existing.get().getId());
      }
    }

    try {
      limiter.consume("recommendation-user:" + profile.getUser().getId(), false);
      limiter.consume("global", true);
      var generated = openAi.explain(facts, profileContext, recommendation.matchScore(), factors);
      if (claimsEligibility(generated.summary())) return deterministic(recommendation);
      var warnings = deterministicWarnings(recommendation.matchFactors());
      var payload = new CachedPayload(generated.summary(), generated.advantages(), warnings);
      persist(profile, recommendation, fingerprint, payload);
      return new RecommendationExplanation(payload.summary(), payload.advantages(), payload.warnings(),
          RecommendationExplanation.Source.OPENAI);
    } catch (RuntimeException error) {
      log.info("AI recommendation explanation fallback type={} id={} reason={}", candidate.type(),
          candidate.id(), error instanceof AiException ? error.getMessage() : error.getClass().getSimpleName());
      return deterministic(recommendation);
    }
  }

  public RecommendationExplanation deterministic(ScoredRecommendation recommendation) {
    var advantages = recommendation.matchFactors().stream().filter(MatchFactor::matched)
        .sorted(Comparator.comparingInt(MatchFactor::score).reversed())
        .map(MatchFactor::explanation).filter(AiRecommendationExplanationService::present)
        .distinct().limit(3).toList();
    var summary = advantages.isEmpty()
        ? "تم اختيار هذه الفرصة وترتيبها بناءً على بيانات ملفك المتاحة."
        : "تم اختيار هذه الفرصة لأنها تتوافق مع أبرز تفضيلات ملفك المسجلة.";
    return new RecommendationExplanation(summary, advantages,
        deterministicWarnings(recommendation.matchFactors()), RecommendationExplanation.Source.FALLBACK);
  }

  private void persist(UserProfile profile, ScoredRecommendation recommendation, String fingerprint,
      CachedPayload payload) {
    var item = new RecommendationExplanationCache();
    item.setUser(profile.getUser());
    item.setRecommendationType(recommendation.candidate().type());
    item.setCandidateId(recommendation.candidate().id());
    item.setInputFingerprint(fingerprint);
    try {
      item.setPayloadJson(mapper.writeValueAsString(payload));
    } catch (Exception error) {
      throw new IllegalStateException("Unable to serialize recommendation explanation", error);
    }
    try {
      cache.deleteByUserIdAndRecommendationTypeAndCandidateId(
          profile.getUser().getId(), recommendation.candidate().type(),
          recommendation.candidate().id());
      cache.saveAndFlush(item);
    } catch (DataIntegrityViolationException duplicate) {
      log.debug("Recommendation explanation was cached concurrently type={} id={}",
          recommendation.candidate().type(), recommendation.candidate().id());
    }
  }

  private static Map<String,Object> opportunityFacts(ScoredRecommendation recommendation) {
    var candidate = recommendation.candidate();
    var facts = new LinkedHashMap<String,Object>();
    put(facts, "type", candidate.type());
    put(facts, "title", candidate.title());
    put(facts, "organization", candidate.organization());
    put(facts, "category", candidate.category());
    put(facts, "country", candidate.country());
    put(facts, "deadline", candidate.deadline() == null ? null : candidate.deadline().toString());
    put(facts, "alwaysOpen", candidate.alwaysOpen());
    put(facts, "funding", candidate.funding());
    put(facts, "acceptedSpecialities", sorted(candidate.specialities()));
    put(facts, "acceptedNationalities", sorted(candidate.nationalities()));
    put(facts, "acceptedDegreeLevels", sorted(candidate.degreeLevels()));
    var jobFacts = new LinkedHashMap<String,Object>();
    SAFE_JOB_ATTRIBUTES.forEach(key -> put(jobFacts, key, candidate.attributes().get(key)));
    put(facts, "jobFacts", jobFacts);
    return facts;
  }

  private static Map<String,Object> profileContext(
      UserProfile profile, ScoredRecommendation recommendation) {
    var context = new LinkedHashMap<String,Object>();
    put(context, "goals", sorted(profile.getGoals()));
    put(context, "currentMajor", profile.getCurrentMajor());
    put(context, "primarySpeciality", profile.getPrimarySpeciality());
    put(context, "degreeLevel", profile.getDegreeLevel());
    put(context, "desiredDegree", profile.getDesiredDegree());
    put(context, "preferredCountries", sorted(profile.getPreferredCountries()));
    put(context, "preferredCategories", sorted(profile.getPreferredCategories()));
    if (!recommendation.candidate().nationalities().isEmpty())
      put(context, "nationality", profile.getNationality());
    if (recommendation.candidate().type().name().equals("JOB"))
      put(context, "skills", sorted(profile.getSkills()));
    return context;
  }

  private static List<Map<String,Object>> factorContext(List<MatchFactor> factors) {
    return factors.stream().map(factor -> {
      Map<String,Object> value = new LinkedHashMap<>();
      value.put("key", factor.key());
      value.put("score", factor.score());
      value.put("maxScore", factor.maxScore());
      value.put("matched", factor.matched());
      value.put("explanation", factor.explanation());
      return value;
    }).toList();
  }

  private String fingerprint(Map<String,Object> facts, Map<String,Object> profileContext,
      int score, List<Map<String,Object>> factors) {
    try {
      var value = new LinkedHashMap<String,Object>();
      value.put("facts", facts);
      value.put("profile", profileContext);
      value.put("score", score);
      value.put("factors", factors);
      var digest = MessageDigest.getInstance("SHA-256")
          .digest(mapper.writeValueAsBytes(value));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (Exception error) {
      throw new IllegalStateException("Unable to fingerprint recommendation explanation", error);
    }
  }

  private static List<String> deterministicWarnings(List<MatchFactor> factors) {
    return factors.stream().filter(factor -> !factor.matched())
        .map(MatchFactor::explanation).filter(AiRecommendationExplanationService::present)
        .distinct().limit(3).toList();
  }

  private static boolean claimsEligibility(String value) {
    var normalized = value.toLowerCase(java.util.Locale.ROOT);
    return normalized.contains("مؤهل بالتأكيد") || normalized.contains("مضمونة")
        || normalized.contains("guaranteed eligible") || normalized.contains("eligibility is guaranteed");
  }

  private static List<String> sorted(Collection<?> values) {
    if (values == null) return List.of();
    return values.stream().filter(Objects::nonNull).map(Object::toString).sorted().toList();
  }

  private static void put(Map<String,Object> target, String key, Object value) {
    if (value == null || value instanceof String text && text.isBlank()
        || value instanceof Collection<?> collection && collection.isEmpty()
        || value instanceof Map<?,?> map && map.isEmpty()) return;
    target.put(key, value);
  }

  private static boolean present(String value) { return value != null && !value.isBlank(); }

  private record CachedPayload(String summary, List<String> advantages, List<String> warnings) {}
}
