package com.jameati.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Places genuinely low-barrier jobs ahead of roles that require more experience. */
final class JobCatalogOrdering {
  private JobCatalogOrdering() {}

  static List<CatalogItem> easyFirst(List<CatalogItem> items) {
    var ordered = new ArrayList<>(items);
    // List.sort is stable, so the requested database order is preserved inside each rank.
    ordered.sort(java.util.Comparator.comparingInt(JobCatalogOrdering::easeRank));
    return List.copyOf(ordered);
  }

  static Optional<CatalogItem> firstEasy(List<CatalogItem> items) {
    return easyFirst(items).stream()
        .filter(item -> easeRank(item) < 3)
        .findFirst();
  }

  private static int easeRank(CatalogItem item) {
    var text = searchableText(item);
    if (containsAny(
        text,
        "no experience required",
        "no prior experience",
        "without experience",
        "بدون خبرة",
        "لا تتطلب خبرة",
        "لا يشترط خبرة")) return 0;
    if (containsAny(
        text,
        "entry-level",
        "entry level",
        "junior",
        "beginner",
        "مبتدئ",
        "مبتدئين")) return 1;
    if (containsAny(
        text,
        "intern",
        "trainee",
        "working student",
        "part time",
        "part-time",
        "assistant",
        "freelance",
        "hourly",
        "بالساعة",
        "عمل مرن")) return 2;
    return 3;
  }

  private static String searchableText(CatalogItem item) {
    var attributes = item.getAttributes();
    return String.join(
            " ",
            value(item.getTitle()),
            value(item.getSummary()),
            value(item.getMeta()),
            value(attributes == null ? null : attributes.get("jobTypes")),
            value(attributes == null ? null : attributes.get("tags")))
        .toLowerCase(Locale.ROOT);
  }

  private static String value(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static boolean containsAny(String text, String... candidates) {
    for (var candidate : candidates) {
      if (text.contains(candidate)) return true;
    }
    return false;
  }
}
