package com.jameati.common;

import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Utilities for persistently classifying titles and applying stable Arabic-first paging. */
public final class ArabicTitleSupport {
  private static final String ARABIC_TITLE_PROPERTY = "arabicTitle";
  private static final String ID_PROPERTY = "id";

  private ArabicTitleSupport() {}

  /**
   * A title is Arabic when its first Unicode letter belongs to the Arabic script.
   * Leading whitespace, punctuation, numbers, emoji and symbols are ignored.
   */
  public static boolean startsWithArabicLetter(String title) {
    if (title == null || title.isBlank()) return false;
    for (int offset = 0; offset < title.length(); ) {
      var codePoint = title.codePointAt(offset);
      if (Character.isLetter(codePoint)) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.ARABIC;
      }
      offset += Character.charCount(codePoint);
    }
    return false;
  }

  /**
   * Prepends the persisted language key, preserves the requested ordering inside each language,
   * and guarantees a deterministic id tie-break for pagination.
   */
  public static Pageable arabicFirst(Pageable pageable) {
    if (pageable == null) throw new IllegalArgumentException("pageable is required");
    var sort = arabicFirst(pageable.getSort());
    if (pageable.isUnpaged()) return Pageable.unpaged(sort);
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
  }

  public static Sort arabicFirst(Sort requested) {
    var orders = new ArrayList<Sort.Order>();
    orders.add(Sort.Order.desc(ARABIC_TITLE_PROPERTY));
    var hasId = false;
    if (requested != null) {
      for (var order : requested) {
        var property = order.getProperty();
        if (property == null || property.isBlank()) continue;
        if (ARABIC_TITLE_PROPERTY.equalsIgnoreCase(property)) continue;
        orders.add(order);
        if (ID_PROPERTY.equals(property.toLowerCase(Locale.ROOT))) hasId = true;
      }
    }
    if (!hasId) orders.add(Sort.Order.desc(ID_PROPERTY));
    return Sort.by(orders);
  }
}
