package com.jameati.recommendation;

import com.jameati.me.UserProfile;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ScholarshipRecommendationScorer implements RecommendationScorer {
  private final Clock clock;

  public ScholarshipRecommendationScorer() { this(Clock.systemUTC()); }
  ScholarshipRecommendationScorer(Clock clock) { this.clock = clock; }

  @Override public boolean supports(RecommendationType type) {
    return type == RecommendationType.SCHOLARSHIP;
  }

  @Override public EligibilityResult eligibility(
      UserProfile profile, RecommendationCandidate candidate) {
    return OpportunityEligibility.evaluate(profile, candidate, LocalDate.now(clock));
  }

  @Override public ScoredRecommendation score(
      UserProfile profile, RecommendationCandidate candidate) {
    var factors = List.of(
        ScoringSupport.factor("field", 25,
            ScoringSupport.intersects(profile.getPreferredSpecialities(), candidate.specialities())
                || ScoringSupport.textMatchesAny(candidate.title(), profile.getPreferredSpecialities()),
            "توافق المجال أو التخصص"),
        ScoringSupport.factor("country", 20,
            ScoringSupport.containsIgnoreCase(profile.getPreferredCountries(), candidate.country()),
            "الدولة ضمن الوجهات المفضلة"),
        ScoringSupport.factor("degree", 20,
            candidate.degreeLevels().isEmpty()
                || ScoringSupport.containsIgnoreCase(candidate.degreeLevels(), profile.getDegreeLevel()),
            "المرحلة الدراسية مقبولة"),
        ScoringSupport.factor("nationality", 20,
            candidate.nationalities().isEmpty()
                || ScoringSupport.containsIgnoreCase(candidate.nationalities(), "ALL")
                || ScoringSupport.containsIgnoreCase(candidate.nationalities(), profile.getNationality()),
            "الجنسية ضمن شروط الأهلية"),
        ScoringSupport.factor("funding", 10,
            profile.getPreferredFunding() != null
                && profile.getPreferredFunding() == candidate.funding(),
            "نوع التمويل يطابق التفضيل"),
        ScoringSupport.factor("interests", 5,
            ScoringSupport.textMatchesAny(candidate.title() + " " + candidate.category(), profile.getInterests()),
            "الفرصة مرتبطة بالاهتمامات"));
    return result(candidate, factors);
  }

  static ScoredRecommendation result(
      RecommendationCandidate candidate, List<MatchFactor> factors) {
    return new ScoredRecommendation(candidate, factors.stream().mapToInt(MatchFactor::score).sum(), factors);
  }
}
