package com.jameati.recommendation;

import com.jameati.catalog.CatalogRepository;
import com.jameati.me.UserProfile;
import com.jameati.onboarding.UserGoal;
import com.jameati.opportunity.OpportunityRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecommendationCandidateService {
  private static final Set<String> SCHOLARSHIP_CATEGORIES = Set.of("منح دراسية", "منح", "Scholarship");
  private static final Set<String> INTERNSHIP_CATEGORIES = Set.of("تدريب", "Internship");

  private final OpportunityRepository opportunities;
  private final CatalogRepository catalog;
  private final int candidateLimit;
  private final Clock clock;

  @Autowired
  public RecommendationCandidateService(
      OpportunityRepository opportunities,
      CatalogRepository catalog,
      @Value("${app.recommendation.candidate-limit:200}") int candidateLimit) {
    this(opportunities, catalog, candidateLimit, Clock.systemUTC());
  }

  RecommendationCandidateService(
      OpportunityRepository opportunities, CatalogRepository catalog, int candidateLimit, Clock clock) {
    this.opportunities = opportunities;
    this.catalog = catalog;
    this.candidateLimit = Math.max(1, Math.min(candidateLimit, 500));
    this.clock = clock;
  }

  public List<RecommendationCandidate> findCandidates(UserProfile profile) {
    var candidates = new ArrayList<RecommendationCandidate>();
    var categories = opportunityCategories(profile.getGoals());
    if (!categories.isEmpty()) {
      var page = PageRequest.of(0, candidateLimit,
          Sort.by(Sort.Order.asc("deadline"), Sort.Order.desc("createdAt")));
      opportunities.findRecommendationCandidates(categories, LocalDate.now(clock), page)
          .map(RecommendationCandidate::from).forEach(candidates::add);
    }
    if (profile.getGoals().contains(UserGoal.FIND_JOB)
        || profile.getGoals().contains(UserGoal.FIND_REMOTE_JOB)) {
      var page = PageRequest.of(0, candidateLimit, Sort.by(Sort.Direction.DESC, "createdAt"));
      catalog.findJobRecommendationCandidates(page)
          .map(RecommendationCandidate::from).forEach(candidates::add);
    }
    return List.copyOf(candidates);
  }

  private static Set<String> opportunityCategories(Set<UserGoal> goals) {
    var categories = new LinkedHashSet<String>();
    if (goals.contains(UserGoal.FIND_SCHOLARSHIP) || goals.contains(UserGoal.STUDY_ABROAD)) {
      categories.addAll(SCHOLARSHIP_CATEGORIES);
    }
    if (goals.contains(UserGoal.FIND_INTERNSHIP) || goals.contains(UserGoal.DEVELOP_SKILLS)) {
      categories.addAll(INTERNSHIP_CATEGORIES);
    }
    return categories;
  }
}
