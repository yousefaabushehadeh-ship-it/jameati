package com.jameati.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.catalog.CatalogItem;
import com.jameati.catalog.CatalogRepository;
import com.jameati.catalog.CatalogType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OfficialCatalogImportTest {
  @Test
  void validatesAndStreamsTheCompleteGeneratedCatalog() throws Exception {
    var repository = mock(CatalogRepository.class);
    when(repository.findAll()).thenReturn(List.of());
    when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result = StartupDataConfiguration.importOfficialCatalog(
        repository,
        new ObjectMapper(),
        List.of(
            "data/official-catalog.json",
            "data/official-expanded-catalog.json",
            "data/official-hourly-work-links.json",
            "data/official-jordan-universities.json"),
        Map.of());

    assertThat(result.imported()).isEqualTo(25_473);
    assertThat(result.updated()).isZero();
    assertThat(result.manualConflicts()).isZero();
    verify(repository, atLeast(50)).saveAll(any());
  }

  @Test
  void importsMultipleFeedsInBatchesWithoutOverwritingManualRowsOrRetiringSelections()
      throws Exception {
    var repository = mock(CatalogRepository.class);
    var unchanged = item(CatalogType.COURSE, "course-unchanged", 10L, managed(
        "feed-a", "same-fingerprint"));
    var manualConflict = item(CatalogType.JOB, "manual-job", 11L, Map.of());
    var stale = item(CatalogType.COURSE, "stale-course", 12L, managed(
        "feed-a", "stale-fingerprint"));
    var protectedStale = item(CatalogType.COURSE, "protected-course", 13L, managed(
        "feed-a", "protected-fingerprint"));
    when(repository.findAll()).thenReturn(List.of(
        unchanged, manualConflict, stale, protectedStale));
    when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result = StartupDataConfiguration.importOfficialCatalog(
        repository,
        new ObjectMapper(),
        List.of("data/catalog-feed-a.json", "data/catalog-feed-b.json"),
        Map.of("COURSE", Set.of(13L)));

    assertThat(result.imported()).isEqualTo(2);
    assertThat(result.updated()).isZero();
    assertThat(result.unchanged()).isEqualTo(1);
    assertThat(result.retired()).isEqualTo(1);
    assertThat(result.manualConflicts()).isEqualTo(1);
    assertThat(result.protectedMissing()).isEqualTo(1);
    assertThat(stale.isDeleted()).isTrue();
    assertThat(stale.isPublished()).isFalse();
    assertThat(protectedStale.isDeleted()).isFalse();
    assertThat(manualConflict.getTitle()).isNull();

    @SuppressWarnings("unchecked")
    var batches = ArgumentCaptor.forClass(List.class);
    verify(repository, org.mockito.Mockito.times(3)).saveAll(batches.capture());
    var saved = batches.getAllValues().stream()
        .flatMap(List<CatalogItem>::stream)
        .toList();
    assertThat(saved).extracting(CatalogItem::getSlug)
        .containsExactlyInAnyOrder("course-new", "university-new", "stale-course");
    var newCourse = saved.stream()
        .filter(record -> "course-new".equals(record.getSlug()))
        .findFirst()
        .orElseThrow();
    assertThat(newCourse.getAttributes())
        .containsEntry("managedBy", "official-catalog")
        .containsEntry("feedId", "feed-a")
        .containsEntry("sourceFingerprint", "new-fingerprint")
        .containsEntry("license", "CC0")
        .containsEntry("level", "advanced");
  }

  @Test
  void migratesAnExistingManagedRorUniversityToItsCuratedJordanianRecord() throws Exception {
    var repository = mock(CatalogRepository.class);
    var attributes = new LinkedHashMap<>(managed("official-expanded-catalog", "old-ror-record"));
    attributes.put("rorId", "https://ror.org/05k89ew48");
    var legacyRorRecord = item(
        CatalogType.UNIVERSITY, "university-university-of-jordan-legacy", 71L, attributes);
    when(repository.findAll()).thenReturn(List.of(legacyRorRecord));
    when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result = StartupDataConfiguration.importOfficialCatalog(
        repository,
        new ObjectMapper(),
        List.of("data/official-jordan-universities.json"),
        Map.of());

    assertThat(result.imported()).isEqualTo(30);
    assertThat(result.updated()).isEqualTo(1);
    assertThat(legacyRorRecord.getSlug()).isEqualTo("jordan-university-university-of-jordan");
    assertThat(legacyRorRecord.getTitle()).isEqualTo("الجامعة الأردنية");
    assertThat(legacyRorRecord.getWebsite()).isEqualTo("https://www.ju.edu.jo/");
    assertThat(legacyRorRecord.getAttributes())
        .containsEntry("feedId", "official-jordan-universities")
        .containsEntry("publicSourceLink", true)
        .containsEntry("location", "الجبيهة، عمّان، الأردن");
  }

  @Test
  void skipsJordanianRorRowsBeforeTheCuratedFeedReplacesLegacyRecords() throws Exception {
    var repository = mock(CatalogRepository.class);
    var attributes = new LinkedHashMap<>(managed("official-expanded-catalog", "old-ror-record"));
    attributes.put("rorId", "https://ror.org/05k89ew48");
    var legacyRorRecord = item(
        CatalogType.UNIVERSITY, "ror-university-of-jordan", 72L, attributes);
    when(repository.findAll()).thenReturn(List.of(legacyRorRecord));
    when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result = StartupDataConfiguration.importOfficialCatalog(
        repository,
        new ObjectMapper(),
        List.of(
            "data/expanded-jordan-ror-fixture.json",
            "data/official-jordan-universities.json"),
        Map.of());

    assertThat(result.imported()).isEqualTo(30);
    assertThat(result.updated()).isEqualTo(1);
    assertThat(result.unchanged()).isEqualTo(1);
    assertThat(legacyRorRecord.getSlug()).isEqualTo("jordan-university-university-of-jordan");
    assertThat(legacyRorRecord.getAttributes())
        .containsEntry("feedId", "official-jordan-universities");
  }

  private static CatalogItem item(
      CatalogType type, String slug, Long id, Map<String, Object> attributes) {
    var item = new CatalogItem();
    item.setId(id);
    item.setType(type);
    item.setSlug(slug);
    item.setPublished(true);
    item.setAttributes(new LinkedHashMap<>(attributes));
    return item;
  }

  private static Map<String, Object> managed(String feedId, String fingerprint) {
    var attributes = new LinkedHashMap<String, Object>();
    attributes.put("managedBy", "official-catalog");
    attributes.put("feedId", feedId);
    attributes.put("sourceFingerprint", fingerprint);
    return attributes;
  }
}
