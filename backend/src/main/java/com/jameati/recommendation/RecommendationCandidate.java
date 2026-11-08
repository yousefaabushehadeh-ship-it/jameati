package com.jameati.recommendation;

import com.jameati.catalog.CatalogItem;
import com.jameati.opportunity.FundingType;
import com.jameati.opportunity.Opportunity;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record RecommendationCandidate(
    RecommendationType type,
    Long id,
    String slug,
    String title,
    String organization,
    String category,
    String country,
    LocalDate deadline,
    boolean alwaysOpen,
    FundingType funding,
    Set<String> specialities,
    Set<String> nationalities,
    Set<String> degreeLevels,
    Map<String, Object> attributes) {

  static RecommendationCandidate from(Opportunity opportunity) {
    return new RecommendationCandidate(
        OpportunityTypeClassifier.classify(opportunity.getCategory()), opportunity.getId(),
        opportunity.getSlug(), opportunity.getTitle(), opportunity.getOrganization(),
        opportunity.getCategory(), opportunity.getCountry(), opportunity.getDeadline(),
        opportunity.isAlwaysOpen(), opportunity.getFunding(), Set.copyOf(opportunity.getSpecialities()),
        Set.copyOf(opportunity.getNationalities()), Set.copyOf(opportunity.getDegreeLevels()), Map.of());
  }

  static RecommendationCandidate from(CatalogItem job) {
    return new RecommendationCandidate(
        RecommendationType.JOB, job.getId(), job.getSlug(), job.getTitle(), job.getOrganization(),
        job.getCategory(), job.getCountry(), null, false, null, Set.of(), Set.of(), Set.of(),
        nonNullAttributes(job.getAttributes()));
  }

  private static Map<String, Object> nonNullAttributes(Map<String, Object> attributes) {
    if (attributes == null || attributes.isEmpty()) return Map.of();
    var values = new LinkedHashMap<String, Object>();
    attributes.forEach((key, value) -> {
      if (key != null && value != null) values.put(key, value);
    });
    return Map.copyOf(values);
  }
}
