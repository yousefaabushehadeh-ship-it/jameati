package com.jameati.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jameati.catalog.CatalogItem;
import com.jameati.catalog.CatalogRepository;
import com.jameati.catalog.CatalogType;
import com.jameati.me.UserProfile;
import com.jameati.onboarding.UserGoal;
import com.jameati.opportunity.FundingType;
import com.jameati.opportunity.Opportunity;
import com.jameati.opportunity.OpportunityRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RecommendationCandidateRepositoryIntegrationTest {
  @Autowired private OpportunityRepository opportunities;
  @Autowired private CatalogRepository catalog;
  @Autowired private RecommendationCandidateService candidateService;
  @Autowired private EntityManager entityManager;

  @Test
  void databaseQueryExcludesExpiredUnpublishedAndUnrelatedCandidates() {
    var today = LocalDate.of(2026, 9, 20);
    opportunities.save(opportunity("eligible-phase3", "منح دراسية", today.plusDays(10), true));
    opportunities.save(opportunity("expired-phase3", "منح دراسية", today.minusDays(1), true));
    opportunities.save(opportunity("draft-phase3", "منح دراسية", today.plusDays(10), false));
    opportunities.save(opportunity("training-phase3", "تدريب", today.plusDays(10), true));
    opportunities.flush();

    var result = opportunities.findRecommendationCandidates(
        Set.of("منح دراسية"), today, PageRequest.of(0, 20));

    assertThat(result.getContent()).extracting(Opportunity::getSlug)
        .contains("eligible-phase3")
        .doesNotContain("expired-phase3", "draft-phase3", "training-phase3");
  }

  @Test
  void jobWithNullMetadataCanBeLoadedForRecommendations() {
    var job = new CatalogItem();
    job.setType(CatalogType.JOB);
    job.setSlug("job-with-null-salary");
    job.setTitle("Remote developer");
    job.setSummary("Remote development role");
    job.setContent("Role details");
    job.setCategory("Engineering");
    job.setOrganization("Example");
    job.setCountry("Jordan");
    var attributes = new LinkedHashMap<String, Object>();
    attributes.put("remote", true);
    attributes.put("salary", null);
    job.setAttributes(attributes);
    catalog.saveAndFlush(job);
    entityManager.clear();

    var profile = new UserProfile();
    profile.setGoals(Set.of(UserGoal.FIND_JOB));
    var candidate = candidateService.findCandidates(profile).stream()
        .filter(item -> "job-with-null-salary".equals(item.slug()))
        .findFirst().orElseThrow();

    assertThat(candidate.attributes()).containsEntry("remote", true).doesNotContainKey("salary");
  }

  private static Opportunity opportunity(
      String slug, String category, LocalDate deadline, boolean published) {
    var item = new Opportunity();
    item.setSlug(slug);
    item.setTitle(slug);
    item.setShortDescription("description");
    item.setDescription("details");
    item.setCategory(category);
    item.setOrganization("organization");
    item.setCountry("Germany");
    item.setFunding(FundingType.FULLY_FUNDED);
    item.setDeadline(deadline);
    item.setPublished(published);
    return item;
  }
}
