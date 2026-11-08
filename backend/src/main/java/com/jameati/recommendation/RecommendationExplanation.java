package com.jameati.recommendation;

import java.util.List;

public record RecommendationExplanation(
    String summary,
    List<String> advantages,
    List<String> warnings,
    Source source) {

  public enum Source { OPENAI, CACHE, FALLBACK }
}
