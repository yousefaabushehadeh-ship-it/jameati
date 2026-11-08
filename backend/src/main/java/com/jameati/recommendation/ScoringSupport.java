package com.jameati.recommendation;

import java.util.Collection;
import java.util.Locale;

final class ScoringSupport {
  private ScoringSupport() {}

  static boolean containsIgnoreCase(Collection<String> values, String wanted) {
    if (values == null || wanted == null || wanted.isBlank()) return false;
    var normalized = wanted.trim().toLowerCase(Locale.ROOT);
    return values.stream().filter(java.util.Objects::nonNull)
        .map(value -> value.trim().toLowerCase(Locale.ROOT)).anyMatch(normalized::equals);
  }

  static boolean intersects(Collection<String> first, Collection<String> second) {
    if (first == null || second == null) return false;
    return first.stream().anyMatch(value -> containsIgnoreCase(second, value));
  }

  static boolean textMatchesAny(String text, Collection<String> values) {
    if (text == null || values == null) return false;
    var normalized = text.toLowerCase(Locale.ROOT);
    return values.stream().filter(java.util.Objects::nonNull)
        .map(value -> value.trim().toLowerCase(Locale.ROOT)).filter(value -> !value.isBlank())
        .anyMatch(normalized::contains);
  }

  static MatchFactor factor(String key, int weight, boolean matched, String explanation) {
    return new MatchFactor(key, matched ? weight : 0, weight, matched, explanation);
  }
}
