package com.jameati.recommendation;

import com.jameati.me.UserProfile;
import com.jameati.onboarding.UserGoal;
import com.jameati.onboarding.WorkMode;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JobRecommendationScorer implements RecommendationScorer {
  @Override public boolean supports(RecommendationType type) { return type == RecommendationType.JOB; }

  @Override public EligibilityResult eligibility(
      UserProfile profile, RecommendationCandidate candidate) {
    var onlyRemote = profile.getGoals().contains(UserGoal.FIND_REMOTE_JOB)
        && !profile.getGoals().contains(UserGoal.FIND_JOB);
    if (onlyRemote && !booleanAttribute(candidate.attributes(), "remote")) {
      return EligibilityResult.failed("REMOTE_REQUIRED");
    }
    var minimumExperience = integerAttribute(candidate.attributes(), "minimumExperienceYears");
    if (minimumExperience != null && profile.getYearsOfExperience() != null
        && profile.getYearsOfExperience() < minimumExperience) {
      return EligibilityResult.failed("INSUFFICIENT_EXPERIENCE");
    }
    return EligibilityResult.passed();
  }

  @Override public ScoredRecommendation score(
      UserProfile profile, RecommendationCandidate candidate) {
    var attributes = candidate.attributes();
    var tags = strings(attributes, "tags");
    var requiredSkills = strings(attributes, "requiredSkills");
    var jobTypes = strings(attributes, "jobTypes");
    var languages = strings(attributes, "languages");
    var minimumExperience = integerAttribute(attributes, "minimumExperienceYears");
    var remote = booleanAttribute(attributes, "remote");
    var factors = List.of(
        ScoringSupport.factor("skills", 30,
            ScoringSupport.intersects(profile.getSkills(), tags)
                || ScoringSupport.intersects(profile.getSkills(), requiredSkills)
                || ScoringSupport.textMatchesAny(candidate.title(), profile.getSkills()),
            "المهارات تتقاطع مع متطلبات الوظيفة"),
        ScoringSupport.factor("field", 20,
            ScoringSupport.textMatchesAny(candidate.category(), profile.getPreferredSpecialities())
                || ScoringSupport.intersects(profile.getPreferredSpecialities(), tags),
            "المجال المهني متوافق"),
        ScoringSupport.factor("experience", 15,
            minimumExperience == null || (profile.getYearsOfExperience() != null
                && profile.getYearsOfExperience() >= minimumExperience),
            "سنوات الخبرة تفي بالمتطلب"),
        ScoringSupport.factor("title", 10,
            ScoringSupport.textMatchesAny(candidate.title(), profile.getPreferredJobTitles()),
            "المسمى الوظيفي مفضل"),
        ScoringSupport.factor("location", 10,
            ScoringSupport.containsIgnoreCase(profile.getPreferredCountries(), candidate.country())
                || (remote && profile.getGoals().contains(UserGoal.FIND_REMOTE_JOB)),
            "الموقع أو الإتاحة عن بُعد مناسبة"),
        ScoringSupport.factor("workMode", 10,
            (profile.getPreferredWorkMode() == WorkMode.REMOTE && remote)
                || profile.getPreferredWorkMode() == WorkMode.FLEXIBLE
                || ScoringSupport.textMatchesAny(profile.getPreferredEmploymentType() == null
                    ? null : profile.getPreferredEmploymentType().name(), jobTypes),
            "نمط العمل يطابق التفضيل"),
        ScoringSupport.factor("language", 5,
            languages.isEmpty() || ScoringSupport.intersects(profile.getLanguages(), languages),
            "اللغات متوافقة"));
    return ScholarshipRecommendationScorer.result(candidate, factors);
  }

  private static boolean booleanAttribute(Map<String, Object> attributes, String key) {
    var value = attributes.get(key);
    return value instanceof Boolean bool ? bool : value != null && Boolean.parseBoolean(value.toString());
  }

  private static Integer integerAttribute(Map<String, Object> attributes, String key) {
    var value = attributes.get(key);
    if (value instanceof Number number) return number.intValue();
    try { return value == null ? null : Integer.valueOf(value.toString()); }
    catch (NumberFormatException ignored) { return null; }
  }

  private static List<String> strings(Map<String, Object> attributes, String key) {
    var value = attributes.get(key);
    if (!(value instanceof Collection<?> collection)) return List.of();
    return collection.stream().filter(java.util.Objects::nonNull).map(Object::toString)
        .map(item -> item.trim().toLowerCase(Locale.ROOT)).filter(item -> !item.isBlank()).toList();
  }
}
