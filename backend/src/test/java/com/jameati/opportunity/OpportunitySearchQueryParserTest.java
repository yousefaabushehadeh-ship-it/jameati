package com.jameati.opportunity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OpportunitySearchQueryParserTest {
  @Test void normalizesArabicVariants() {
    assertEquals("الاردن", OpportunitySearchQueryParser.normalize("الأُرْدُن"));
    assertEquals("المانيا", OpportunitySearchQueryParser.normalize("ألمانيا"));
  }
  @Test void parsesStudyAndCountry() {
    var intent = OpportunitySearchQueryParser.parse("الدراسة في الاردن");
    assertEquals("منح دراسية", intent.category());
    assertEquals("الأردن", intent.country());
    assertTrue(intent.keywords().isEmpty());
  }
  @Test void parsesCombinedIntent() {
    var intent = OpportunitySearchQueryParser.parse("بدي منحة ماجستير ممولة بالكامل في ألمانيا");
    assertEquals("ألمانيا", intent.country());
    assertEquals("منح دراسية", intent.category());
    assertEquals("ماجستير", intent.degree());
    assertEquals(FundingType.FULLY_FUNDED, intent.funding());
  }
  @Test void parsesMultiwordCountryBeforeTokens() {
    var intent = OpportunitySearchQueryParser.parse("fully funded scholarships in United States");
    assertEquals("الولايات المتحدة", intent.country());
    assertEquals(FundingType.FULLY_FUNDED, intent.funding());
    assertTrue(intent.keywords().isEmpty());
  }
  @Test void parsesTrainingAndField() {
    var intent = OpportunitySearchQueryParser.parse("تدريب برمجة في الإمارات");
    assertEquals("تدريب", intent.category());
    assertEquals("الإمارات", intent.country());
    assertTrue(intent.fields().contains("programming"));
  }
  @Test void parsesRemoteJobs() {
    var intent = OpportunitySearchQueryParser.parse("وظائف برمجة عن بعد");
    assertEquals("وظائف", intent.category());
    assertTrue(intent.remote());
    assertTrue(intent.fields().contains("programming"));
  }
  @Test void preservesUnknownWordsForTextMatching() {
    assertEquals(java.util.List.of("cybersecurity"), OpportunitySearchQueryParser.parse("منح cybersecurity في بريطانيا").keywords());
  }
}
