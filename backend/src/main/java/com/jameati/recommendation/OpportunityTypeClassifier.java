package com.jameati.recommendation;

import java.util.Locale;

final class OpportunityTypeClassifier {
  private OpportunityTypeClassifier() {}

  static RecommendationType classify(String category) {
    var normalized = category == null ? "" : category.toLowerCase(Locale.ROOT);
    if (normalized.contains("منح") || normalized.contains("scholarship")) {
      return RecommendationType.SCHOLARSHIP;
    }
    if (normalized.contains("تدريب") || normalized.contains("internship")) {
      return RecommendationType.INTERNSHIP;
    }
    return RecommendationType.OPPORTUNITY;
  }
}
