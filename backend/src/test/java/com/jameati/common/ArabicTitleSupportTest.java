package com.jameati.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class ArabicTitleSupportTest {
  @Test
  void detectsTheScriptOfTheFirstUnicodeLetter() {
    assertThat(ArabicTitleSupport.startsWithArabicLetter("  🎓 2026 — منحة جامعية")).isTrue();
    assertThat(ArabicTitleSupport.startsWithArabicLetter("پژوهش دانشگاهی")).isTrue();
    assertThat(ArabicTitleSupport.startsWithArabicLetter("Upwork — بوابة وظائف عربية")).isFalse();
    assertThat(ArabicTitleSupport.startsWithArabicLetter("2026 — Scholarship منحة")).isFalse();
    assertThat(ArabicTitleSupport.startsWithArabicLetter("  2026 — 🎓")).isFalse();
  }

  @Test
  void prependsArabicAndKeepsRequestedSortWithStableIdTieBreak() {
    var pageable = ArabicTitleSupport.arabicFirst(
        PageRequest.of(2, 7, Sort.by(Sort.Order.desc("createdAt"))));

    assertThat(pageable.getPageNumber()).isEqualTo(2);
    assertThat(pageable.getPageSize()).isEqualTo(7);
    assertThat(pageable.getSort().toList())
        .extracting(Sort.Order::getProperty)
        .containsExactly("arabicTitle", "createdAt", "id");
    assertThat(pageable.getSort().getOrderFor("arabicTitle").getDirection())
        .isEqualTo(Sort.Direction.DESC);
    assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
        .isEqualTo(Sort.Direction.DESC);
    assertThat(pageable.getSort().getOrderFor("id").getDirection())
        .isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void cannotOverrideArabicPriorityAndDoesNotDuplicateRequestedIdSort() {
    var sorted = ArabicTitleSupport.arabicFirst(Sort.by(
        Sort.Order.asc("arabicTitle"),
        Sort.Order.desc("id")));

    assertThat(sorted.toList()).extracting(Sort.Order::getProperty)
        .containsExactly("arabicTitle", "id");
    assertThat(sorted.getOrderFor("arabicTitle").getDirection()).isEqualTo(Sort.Direction.DESC);
    assertThat(sorted.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
  }
}
