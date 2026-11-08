package com.jameati.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jameati.subscription.ContentAccessService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CatalogPublicSearchIntegrationTest {
  @Autowired private CatalogRepository repository;

  @Test
  void searchesAllPublicMetadataAndAppliesTheCategoryOnTheDatabasePage() {
    repository.save(item(
        "database-search-course-a",
        "تحليل البيانات",
        "دورة عملية",
        "تقنية وبرمجة",
        "جامعة القدس",
        "فلسطين"));
    repository.save(item(
        "database-search-course-b",
        "إدارة الفرق",
        "دورة إدارية",
        "أعمال وإدارة",
        "مؤسسة دولية",
        "الأردن"));

    var byCountry = repository.publicSearch(
        CatalogType.COURSE, "فلسطين", "", PageRequest.of(0, 24));
    var byOrganizationAndCategory = repository.publicSearch(
        CatalogType.COURSE, "القدس", "تقنية وبرمجة", PageRequest.of(0, 24));
    var wrongCategory = repository.publicSearch(
        CatalogType.COURSE, "القدس", "أعمال وإدارة", PageRequest.of(0, 24));

    assertThat(byCountry.getContent()).extracting(CatalogItem::getSlug)
        .contains("database-search-course-a");
    assertThat(byOrganizationAndCategory.getContent()).extracting(CatalogItem::getSlug)
        .containsExactly("database-search-course-a");
    assertThat(wrongCategory).isEmpty();
  }

  @Test
  void jobsPutArabicTitlesFirstAcrossPagesAndKeepRequestedSortWithinEachLanguage() {
    var marker = "arabic-ordering-job-unique";
    repository.saveAll(List.of(
        job(marker + "-arabic-b", "وظيفة باء", marker),
        job(marker + "-english-b", "Zulu remote job", marker),
        job(marker + "-arabic-a", "💼 وظيفة ألف", marker),
        job(marker + "-english-a", "Upwork — بوابة وظائف", marker)));
    repository.flush();

    var access = mock(ContentAccessService.class);
    var context = mock(ContentAccessService.AccessContext.class);
    when(access.resolve((Principal) null)).thenReturn(context);
    var controller = new CatalogController(repository, access);
    var requested = Sort.by(Sort.Order.asc("slug"));

    var firstPage = controller.list(
        CatalogType.JOB, marker, "", false, false, PageRequest.of(0, 2, requested), null).data();
    var secondPage = controller.list(
        CatalogType.JOB, marker, "", false, false, PageRequest.of(1, 2, requested), null).data();

    assertThat(firstPage.getContent()).extracting(CatalogController.Item::slug)
        .containsExactly(marker + "-arabic-a", marker + "-arabic-b");
    assertThat(secondPage.getContent()).extracting(CatalogController.Item::slug)
        .containsExactly(marker + "-english-a", marker + "-english-b");
    assertThat(firstPage.getTotalElements()).isEqualTo(4);
  }

  @Test
  void easyJobOrderingStartsWithAJobThatRequiresNoExperience() {
    var marker = "easy-job-ordering-unique";
    repository.saveAll(List.of(
        jobWithTypes(
            marker + "-regular",
            "وظيفة إدارية بخبرة",
            marker,
            List.of("Experienced", "Full time")),
        jobWithTypes(
            marker + "-junior",
            "Junior support role",
            marker,
            List.of("Entry-Level", "Full time")),
        jobWithTypes(
            marker + "-no-experience",
            "Marketing intern",
            marker,
            List.of("Internship", "no experience required / student"))));
    repository.flush();

    var access = mock(ContentAccessService.class);
    var context = mock(ContentAccessService.AccessContext.class);
    when(access.resolve((Principal) null)).thenReturn(context);
    var controller = new CatalogController(repository, access);

    var page = controller.list(
        CatalogType.JOB,
        marker,
        "",
        false,
        true,
        PageRequest.of(0, 3, Sort.by(Sort.Order.asc("slug"))),
        null).data();

    assertThat(page.getContent()).extracting(CatalogController.Item::slug)
        .containsExactly(
            marker + "-no-experience",
            marker + "-junior",
            marker + "-regular");
  }

  @Test
  void universityCountryMixIsStableAcrossDatabasePages() {
    var marker = "university-country-mix-unique";
    repository.saveAll(List.of(
        university("mix-jordan-1", marker, "الأردن"),
        university("mix-jordan-2", marker, "الأردن"),
        university("mix-jordan-3", marker, "الأردن"),
        university("mix-jordan-4", marker, "الأردن"),
        university("mix-jordan-5", marker, "الأردن"),
        university("mix-jordan-6", marker, "الأردن"),
        princessSumayaUniversity("mix-jordan-psut", marker),
        university("mix-us-1", marker, "الولايات المتحدة"),
        university("mix-us-2", marker, "الولايات المتحدة"),
        university("mix-canada-1", marker, "كندا"),
        university("mix-canada-2", marker, "كندا"),
        university("mix-other-1", marker, "ألمانيا")));
    repository.flush();

    var access = mock(ContentAccessService.class);
    var context = mock(ContentAccessService.AccessContext.class);
    when(access.resolve((Principal) null)).thenReturn(context);
    var controller = new CatalogController(repository, access);
    var requested = Sort.by(Sort.Order.asc("slug"));

    var firstPage = controller.list(
        CatalogType.UNIVERSITY, marker, "", true, false, PageRequest.of(0, 5, requested), null).data();
    var secondPage = controller.list(
        CatalogType.UNIVERSITY, marker, "", true, false, PageRequest.of(1, 5, requested), null).data();
    var thirdPage = controller.list(
        CatalogType.UNIVERSITY, marker, "", true, false, PageRequest.of(2, 5, requested), null).data();

    assertThat(firstPage.getContent()).extracting(CatalogController.Item::slug)
        .containsExactly("mix-jordan-psut", "mix-us-1", "mix-canada-1", "mix-jordan-1", "mix-jordan-2");
    assertThat(secondPage.getContent()).extracting(CatalogController.Item::slug)
        .containsExactly("mix-jordan-3", "mix-us-2", "mix-canada-2", "mix-jordan-4", "mix-jordan-5");
    assertThat(thirdPage.getContent()).extracting(CatalogController.Item::slug)
        .containsExactly("mix-jordan-6", "mix-other-1");
    assertThat(firstPage.getTotalElements()).isEqualTo(12);
  }

  private static CatalogItem item(
      String slug,
      String title,
      String summary,
      String category,
      String organization,
      String country) {
    var item = new CatalogItem();
    item.setType(CatalogType.COURSE);
    item.setSlug(slug);
    item.setTitle(title);
    item.setSummary(summary);
    item.setContent("محتوى اختباري");
    item.setCategory(category);
    item.setOrganization(organization);
    item.setCountry(country);
    item.setWebsite("https://example.com/" + slug);
    item.setPublished(true);
    return item;
  }

  private static CatalogItem job(String slug, String title, String marker) {
    var item = item(slug, title, marker, "عمل حر", "منصة عمل", "عن بُعد");
    item.setType(CatalogType.JOB);
    item.setAttributes(Map.of("publicSourceLink", true));
    return item;
  }

  private static CatalogItem jobWithTypes(
      String slug, String title, String marker, List<String> jobTypes) {
    var item = job(slug, title, marker);
    item.setAttributes(Map.of(
        "publicSourceLink", true,
        "jobTypes", jobTypes));
    return item;
  }

  private static CatalogItem university(String slug, String marker, String country) {
    var item = item(slug, slug, marker, "جامعة", slug, country);
    item.setType(CatalogType.UNIVERSITY);
    item.setAttributes(Map.of("publicSourceLink", true));
    return item;
  }

  private static CatalogItem princessSumayaUniversity(String slug, String marker) {
    var item = university(slug, marker, "الأردن");
    item.setTitle("جامعة الأميرة سمية للتكنولوجيا");
    return item;
  }
}
