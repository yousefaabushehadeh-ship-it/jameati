package com.jameati.catalog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * Keeps the university directory locally relevant while exposing destinations in a predictable,
 * repeatable rhythm. The sequence is Jordan, United States, Canada, Jordan, Jordan.
 */
final class UniversityCatalogOrdering {
  private static final List<Bucket> COUNTRY_CYCLE = List.of(
      Bucket.JORDAN,
      Bucket.UNITED_STATES,
      Bucket.CANADA,
      Bucket.JORDAN,
      Bucket.JORDAN);

  private UniversityCatalogOrdering() {}

  static List<CatalogItem> interleave(List<CatalogItem> items) {
    if (items.size() < 2) return List.copyOf(items);

    var buckets = new EnumMap<Bucket, ArrayDeque<CatalogItem>>(Bucket.class);
    for (var bucket : Bucket.values()) buckets.put(bucket, new ArrayDeque<>());
    var other = new ArrayDeque<CatalogItem>();

    for (var item : items) {
      var bucket = Bucket.from(item.getCountry());
      if (bucket == Bucket.OTHER) other.add(item);
      else if (bucket == Bucket.JORDAN && isPrincessSumaya(item)) buckets.get(bucket).addFirst(item);
      else buckets.get(bucket).add(item);
    }

    var ordered = new ArrayList<CatalogItem>(items.size());
    var cycleIndex = 0;
    while (hasItems(buckets, other)) {
      var next = pollPreferred(buckets, cycleIndex);
      if (next == null) next = other.poll();
      if (next == null) break;
      ordered.add(next);
      cycleIndex = (cycleIndex + 1) % COUNTRY_CYCLE.size();
    }
    return List.copyOf(ordered);
  }

  private static boolean isPrincessSumaya(CatalogItem item) {
    var title = item.getTitle() == null ? "" : item.getTitle().trim();
    var slug = item.getSlug() == null ? "" : item.getSlug().trim().toLowerCase(Locale.ROOT);
    return "جامعة الأميرة سمية للتكنولوجيا".equals(title)
        || title.toLowerCase(Locale.ROOT).contains("princess sumaya")
        || slug.contains("princess-sumaya");
  }

  private static boolean hasItems(
      EnumMap<Bucket, ArrayDeque<CatalogItem>> buckets, ArrayDeque<CatalogItem> other) {
    return !other.isEmpty() || buckets.values().stream().anyMatch(queue -> !queue.isEmpty());
  }

  private static CatalogItem pollPreferred(
      EnumMap<Bucket, ArrayDeque<CatalogItem>> buckets, int cycleIndex) {
    for (var offset = 0; offset < COUNTRY_CYCLE.size(); offset += 1) {
      var bucket = COUNTRY_CYCLE.get((cycleIndex + offset) % COUNTRY_CYCLE.size());
      var item = buckets.get(bucket).poll();
      if (item != null) return item;
    }
    return null;
  }

  private enum Bucket {
    JORDAN,
    UNITED_STATES,
    CANADA,
    OTHER;

    static Bucket from(String country) {
      return switch (country == null ? "" : country.trim()) {
        case "الأردن", "Jordan" -> JORDAN;
        case "الولايات المتحدة", "الولايات المتحدة الأمريكية", "United States", "United States of America" -> UNITED_STATES;
        case "كندا", "Canada" -> CANADA;
        default -> OTHER;
      };
    }
  }
}
