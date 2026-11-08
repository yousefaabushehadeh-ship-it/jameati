package com.jameati.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jameati.subscription.ContentAccessService;
import com.jameati.subscription.SubscriptionPlanService;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class CatalogContentAccessTest {
  @Test
  void lockedItemKeepsPreviewButRemovesPremiumFields() {
    var item = new CatalogItem();
    item.setId(7L);
    item.setSlug("locked-course");
    item.setTitle("دورة تجريبية");
    item.setSummary("معاينة قصيرة");
    item.setContent("محتوى سري");
    item.setWebsite("https://secret.example");
    item.setMeta("بيانات تفصيلية");
    item.setAttributes(Map.of("sourceUrl", "https://secret.example/source"));

    var response = CatalogController.Item.of(item, true);

    assertThat(response.title()).isEqualTo("دورة تجريبية");
    assertThat(response.summary()).isEqualTo("معاينة قصيرة");
    assertThat(response.content()).isNull();
    assertThat(response.website()).isNull();
    assertThat(response.meta()).isNull();
    assertThat(response.attributes()).isEmpty();
    assertThat(response.accessLocked()).isTrue();
  }

  @Test
  void homepageLoadsConfiguredOldSelectionsWithTwoBoundedRepositoryQueries() {
    var repository = mock(CatalogRepository.class);
    var access = mock(ContentAccessService.class);
    var plan = new SubscriptionPlanService.PlanConfig(
        true,
        BigDecimal.TEN,
        "USD",
        12,
        4,
        Map.of(CatalogType.COURSE.name(), List.of(2L, 100L)),
        "");
    var context = new ContentAccessService.AccessContext(false, false, null, plan);
    var oldSelection = item(2L, "old-selection");
    var latestSelection = item(100L, "latest-selection");
    var latestSecond = item(99L, "latest-second");
    var latestThird = item(98L, "latest-third");
    var latestFourth = item(97L, "latest-fourth");
    when(access.resolve((Principal) null)).thenReturn(context);
    when(repository.findByTypeAndIdInAndPublishedTrueAndDeletedFalse(
        CatalogType.COURSE, List.of(2L, 100L)))
        .thenReturn(List.of(latestSelection, oldSelection));
    when(repository.findByTypeAndPublishedTrueAndDeletedFalse(
        org.mockito.ArgumentMatchers.eq(CatalogType.COURSE),
        org.mockito.ArgumentMatchers.any()))
        .thenReturn(new PageImpl<>(
            List.of(latestSelection, latestSecond, latestThird, latestFourth),
            PageRequest.of(0, 4),
            6_955));

    var result = new CatalogController(repository, access)
        .homepage(CatalogType.COURSE, 4, null)
        .data();

    assertThat(result.getTotalElements()).isEqualTo(6_955);
    assertThat(result.getContent()).extracting(CatalogController.Item::id)
        .containsExactly(2L, 100L, 99L, 98L);
    verify(repository, times(1)).findByTypeAndIdInAndPublishedTrueAndDeletedFalse(
        CatalogType.COURSE, List.of(2L, 100L));
    verify(repository, times(1)).findByTypeAndPublishedTrueAndDeletedFalse(
        org.mockito.ArgumentMatchers.eq(CatalogType.COURSE),
        argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 4));
  }

  @Test
  void attributedExternalJobKeepsItsRequiredPublicSourceLink() {
    var repository = mock(CatalogRepository.class);
    var access = mock(ContentAccessService.class);
    var context = mock(ContentAccessService.AccessContext.class);
    var job = item(50L, "external-job");
    job.setType(CatalogType.JOB);
    job.setWebsite("https://jobs.example/external-job");
    job.setAttributes(Map.of("publicSourceLink", true, "sourceName", "Job source"));
    when(access.resolve((Principal) null)).thenReturn(context);
    when(repository.findByTypeAndPublishedTrueAndDeletedFalse(
        org.mockito.ArgumentMatchers.eq(CatalogType.JOB),
        org.mockito.ArgumentMatchers.any()))
        .thenReturn(new PageImpl<>(List.of(job), PageRequest.of(0, 4), 1));

    var response = new CatalogController(repository, access)
        .homepage(CatalogType.JOB, 4, null)
        .data()
        .getContent()
        .getFirst();

    assertThat(response.accessLocked()).isFalse();
    assertThat(response.website()).isEqualTo("https://jobs.example/external-job");
    assertThat(response.attributes()).containsEntry("sourceName", "Job source");
    verify(access, org.mockito.Mockito.never())
        .isCatalogLocked(context, CatalogType.JOB, 50L);
  }

  @Test
  void jobHomepageSortsMergedSelectionsArabicFirstButLeavesTheirRelativeOrderStable() {
    var repository = mock(CatalogRepository.class);
    var access = mock(ContentAccessService.class);
    var plan = new SubscriptionPlanService.PlanConfig(
        true, BigDecimal.TEN, "USD", 12, 4,
        Map.of(CatalogType.JOB.name(), List.of(2L)), "");
    var context = new ContentAccessService.AccessContext(false, false, null, plan);
    var selectedEnglish = item(2L, "selected-english");
    selectedEnglish.setType(CatalogType.JOB);
    selectedEnglish.setArabicTitle(false);
    var latestArabic = item(3L, "latest-arabic");
    latestArabic.setType(CatalogType.JOB);
    latestArabic.setArabicTitle(true);
    var latestEnglish = item(4L, "latest-english");
    latestEnglish.setType(CatalogType.JOB);

    when(access.resolve((Principal) null)).thenReturn(context);
    when(repository.findByTypeAndIdInAndPublishedTrueAndDeletedFalse(
        CatalogType.JOB, List.of(2L))).thenReturn(List.of(selectedEnglish));
    when(repository.findByTypeAndPublishedTrueAndDeletedFalse(
        org.mockito.ArgumentMatchers.eq(CatalogType.JOB),
        org.mockito.ArgumentMatchers.any()))
        .thenReturn(new PageImpl<>(List.of(latestArabic, latestEnglish), PageRequest.of(0, 3), 3));

    var result = new CatalogController(repository, access)
        .homepage(CatalogType.JOB, 3, null)
        .data();

    assertThat(result.getContent()).extracting(CatalogController.Item::id)
        .containsExactly(3L, 2L, 4L);
    verify(repository).findByTypeAndPublishedTrueAndDeletedFalse(
        org.mockito.ArgumentMatchers.eq(CatalogType.JOB),
        argThat(pageable -> pageable.getSort().toList().getFirst().getProperty()
            .equals("arabicTitle")));
  }

  @Test
  void jobHomepagePinsANoExperienceRoleAheadOfTheLatestJobs() {
    var repository = mock(CatalogRepository.class);
    var access = mock(ContentAccessService.class);
    var context = mock(ContentAccessService.AccessContext.class);
    var easyJob = item(51L, "easy-job");
    easyJob.setType(CatalogType.JOB);
    easyJob.setMeta("Internship · no experience required / student");
    easyJob.setAttributes(Map.of("publicSourceLink", true));
    var latestJob = item(52L, "latest-job");
    latestJob.setType(CatalogType.JOB);
    when(access.resolve((Principal) null)).thenReturn(context);
    when(repository.findByTypeAndPublishedTrueAndDeletedFalse(
        org.mockito.ArgumentMatchers.eq(CatalogType.JOB),
        org.mockito.ArgumentMatchers.any()))
        .thenReturn(new PageImpl<>(List.of(latestJob), PageRequest.of(0, 2), 2));
    when(repository.publicSearchAll(
        org.mockito.ArgumentMatchers.eq(CatalogType.JOB),
        org.mockito.ArgumentMatchers.eq(""),
        org.mockito.ArgumentMatchers.eq(""),
        org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(latestJob, easyJob));

    var result = new CatalogController(repository, access)
        .homepage(CatalogType.JOB, 2, null)
        .data();

    assertThat(result.getContent()).extracting(CatalogController.Item::id)
        .containsExactly(51L, 52L);
  }

  private static CatalogItem item(Long id, String slug) {
    var item = new CatalogItem();
    item.setId(id);
    item.setType(CatalogType.COURSE);
    item.setSlug(slug);
    item.setTitle(slug);
    item.setSummary("summary");
    item.setContent("content");
    item.setCategory("category");
    item.setPublished(true);
    item.setAttributes(Map.of());
    return item;
  }
}
