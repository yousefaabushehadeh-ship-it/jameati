package com.jameati.ai;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AiRecommendationDtos {
  private AiRecommendationDtos() {}

  public enum CandidateType {
    OPPORTUNITY,
    JOB
  }

  public enum Engine {
    OPENAI,
    RULES
  }

  public record RecommendationBundle(
      int profileCompleteness,
      List<String> missingProfileFields,
      boolean cvIncluded,
      Engine engine,
      String notice,
      Instant generatedAt,
      List<RecommendationItem> recommendations) {}

  public record RecommendationItem(
      Candidate candidate,
      int matchPercentage,
      String matchLevel,
      List<String> reasons,
      List<String> missingRequirements,
      List<String> nextSteps,
      boolean eligibilityUncertain,
      boolean aiAnalyzed) {}

  public record Candidate(
      String key,
      CandidateType type,
      Long id,
      String slug,
      String route,
      String title,
      String summary,
      String organization,
      String country,
      String category,
      String funding,
      LocalDate deadline,
      String deadlineText,
      boolean alwaysOpen,
      String coverImage,
      String sourceName,
      String originalUrl,
      boolean accessLocked) {}
}
