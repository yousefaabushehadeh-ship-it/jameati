package com.jameati.recommendation;

import com.jameati.me.UserProfile;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OpportunityRecommendationScorer implements RecommendationScorer {
  private final Clock clock;

  public OpportunityRecommendationScorer() { this(Clock.systemUTC()); }
  OpportunityRecommendationScorer(Clock clock) { this.clock = clock; }

  @Override public boolean supports(RecommendationType type) {
    return type == RecommendationType.INTERNSHIP || type == RecommendationType.OPPORTUNITY;
  }

  @Override public EligibilityResult eligibility(
      UserProfile profile, RecommendationCandidate candidate) {
    return OpportunityEligibility.evaluate(profile, candidate, LocalDate.now(clock));
  }

  @Override public ScoredRecommendation score(
      UserProfile profile, RecommendationCandidate candidate) {
    var factors = List.of(
        ScoringSupport.factor("goal", 25, true, "نوع الفرصة مرتبط بأحد الأهداف"),
        ScoringSupport.factor("field", 25,
            ScoringSupport.intersects(profile.getPreferredSpecialities(), candidate.specialities())
                || ScoringSupport.textMatchesAny(candidate.title(), profile.getPreferredSpecialities()),
            "توافق المجال أو التخصص"),
        ScoringSupport.factor("country", 20,
            ScoringSupport.containsIgnoreCase(profile.getPreferredCountries(), candidate.country()),
            "الدولة ضمن الوجهات المفضلة"),
        ScoringSupport.factor("degree", 15,
            candidate.degreeLevels().isEmpty()
                || ScoringSupport.containsIgnoreCase(candidate.degreeLevels(), profile.getDegreeLevel()),
            "المرحلة الدراسية مقبولة"),
        ScoringSupport.factor("interests", 10,
            ScoringSupport.textMatchesAny(candidate.title() + " " + candidate.category(), profile.getInterests()),
            "الفرصة مرتبطة بالاهتمامات"),
        ScoringSupport.factor("funding", 5,
            profile.getPreferredFunding() != null
                && profile.getPreferredFunding() == candidate.funding(),
            "نوع التمويل يطابق التفضيل"));
    return ScholarshipRecommendationScorer.result(candidate, factors);
  }
}
