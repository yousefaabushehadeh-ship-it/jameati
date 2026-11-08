package com.jameati.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.catalog.CatalogItem;
import com.jameati.catalog.CatalogRepository;
import com.jameati.catalog.CatalogType;
import com.jameati.me.CvDocument;
import com.jameati.me.CvDocumentRepository;
import com.jameati.me.UserProfile;
import com.jameati.me.UserProfileRepository;
import com.jameati.opportunity.FundingType;
import com.jameati.opportunity.Opportunity;
import com.jameati.opportunity.OpportunityRepository;
import com.jameati.subscription.ContentAccessService;
import com.jameati.user.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class AiRecommendationServiceTest {
  private UserProfileRepository profiles;
  private CvDocumentRepository cvs;
  private OpportunityRepository opportunities;
  private CatalogRepository catalog;
  private ContentAccessService contentAccess;
  private AiRecommendationService service;
  private User user;
  private ContentAccessService.AccessContext access;

  @BeforeEach
  void setUp() {
    profiles = mock(UserProfileRepository.class);
    cvs = mock(CvDocumentRepository.class);
    opportunities = mock(OpportunityRepository.class);
    catalog = mock(CatalogRepository.class);
    contentAccess = mock(ContentAccessService.class);
    service = new AiRecommendationService(
        profiles, cvs, opportunities, catalog, contentAccess, new ObjectMapper());
    user = new User("student@example.com", "hash", "طالب");
    user.setId(42L);
    access = new ContentAccessService.AccessContext(true, true, null, null);
    when(contentAccess.resolve(user)).thenReturn(access);
    when(cvs.findByUserIdAndDeletedFalseOrderByUpdatedAtDesc(42L)).thenReturn(List.of());
    when(opportunities.findByPublishedTrueAndDeletedFalse(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
    when(catalog.findByTypeAndPublishedTrueAndDeletedFalse(eq(CatalogType.JOB), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
  }

  @Test
  void ranksRealScholarshipsAndJobsWithoutInventingCandidates() {
    var profile = completeProfile();
    profile.setPreferredCategories(Set.of("وظائف", "منح دراسية"));
    profile.setSkills(Set.of("Java", "Spring Boot"));
    when(profiles.findByUserIdAndDeletedFalse(42L)).thenReturn(Optional.of(profile));

    var scholarship = opportunity(11L, "منح دراسية", "الأردن");
    scholarship.setSpecialities(Set.of("علوم الحاسوب"));
    scholarship.setDegreeLevels(Set.of("بكالوريوس"));
    scholarship.setNationalities(Set.of("ALL"));
    scholarship.setSourceName("المصدر الرسمي");
    scholarship.setSourceUrl("https://official.example/scholarship");
    scholarship.setDescription("منحة لطلبة علوم الحاسوب وتطوير Java");
    var job = job(77L);
    job.setSummary("وظيفة Java وSpring Boot عن بعد");
    job.setWebsite("https://jobs.example/77");
    job.setAttributes(Map.of("skills", List.of("Java", "Spring Boot"), "publicSourceLink", true));

    when(opportunities.findByPublishedTrueAndDeletedFalse(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(scholarship)));
    when(catalog.findByTypeAndPublishedTrueAndDeletedFalse(eq(CatalogType.JOB), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(job)));

    var result = service.localRecommendations(user, 12);

    assertThat(result.recommendations()).hasSize(2);
    assertThat(result.recommendations()).extracting(item -> item.candidate().key())
        .containsExactlyInAnyOrder("OPPORTUNITY:11", "JOB:77");
    assertThat(result.recommendations()).allSatisfy(item -> {
      assertThat(item.matchPercentage()).isBetween(0, 100);
      assertThat(item.reasons()).isNotEmpty();
      assertThat(item.nextSteps()).isNotEmpty();
      assertThat(item.aiAnalyzed()).isFalse();
    });
    assertThat(result.recommendations().stream()
        .filter(item -> item.candidate().key().equals("JOB:77"))
        .findFirst().orElseThrow().candidate().originalUrl()).isEqualTo("https://jobs.example/77");
  }

  @Test
  void excludesExpiredOpportunitiesAndDoesNotLeakLockedSourceLinks() {
    when(profiles.findByUserIdAndDeletedFalse(42L)).thenReturn(Optional.of(completeProfile()));
    var expired = opportunity(1L, "منحة", "الأردن");
    expired.setDeadline(LocalDate.now().minusDays(1));
    var locked = opportunity(2L, "منحة", "الأردن");
    locked.setSourceUrl("https://private.example/source");
    when(opportunities.findByPublishedTrueAndDeletedFalse(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(expired, locked)));
    when(contentAccess.isOpportunityLocked(access, 2L)).thenReturn(true);

    var result = service.localRecommendations(user, 12);

    assertThat(result.recommendations()).hasSize(1);
    var candidate = result.recommendations().getFirst().candidate();
    assertThat(candidate.key()).isEqualTo("OPPORTUNITY:2");
    assertThat(candidate.accessLocked()).isTrue();
    assertThat(candidate.originalUrl()).isNull();
    assertThat(candidate.sourceName()).isNull();
  }

  @Test
  void locallyUsesSkillsFromPrimaryCvWithoutReturningRawContactData() {
    var profile = completeProfile();
    profile.setSkills(Set.of());
    profile.setExperienceSummary(null);
    when(profiles.findByUserIdAndDeletedFalse(42L)).thenReturn(Optional.of(profile));
    var cv = new CvDocument();
    cv.setPrimaryDocument(true);
    cv.setContentJson("""
        {"email":"private@example.com","phone":"+970000000","skills":"Python، تحليل بيانات",
         "experience":[{"title":"محلل","description":"خبرة عملية"}]}
        """);
    when(cvs.findByUserIdAndDeletedFalseOrderByUpdatedAtDesc(42L)).thenReturn(List.of(cv));
    var opportunity = opportunity(9L, "تدريب", "الأردن");
    opportunity.setDescription("تدريب في Python وتحليل بيانات");
    when(opportunities.findByPublishedTrueAndDeletedFalse(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(opportunity)));

    var result = service.localRecommendations(user, 12);

    assertThat(result.cvIncluded()).isTrue();
    assertThat(result.missingProfileFields()).doesNotContain("المهارات", "الخبرة أو السيرة الذاتية");
    assertThat(result.recommendations().getFirst().reasons())
        .anyMatch(reason -> reason.contains("مهارات"));
    assertThat(result.toString()).doesNotContain("private@example.com", "+970000000");
  }

  private UserProfile completeProfile() {
    var profile = new UserProfile();
    profile.setUser(user);
    profile.setDateOfBirth(LocalDate.of(2001, 1, 1));
    profile.setCountry("الأردن");
    profile.setNationality("الأردن");
    profile.setDegreeLevel("بكالوريوس");
    profile.setPrimarySpeciality("علوم الحاسوب");
    profile.setInstitution("جامعة");
    profile.setEnglishLevel("B2");
    profile.setExperienceSummary("مشروع برمجي");
    profile.setRemotePreferred(true);
    profile.setPreferredFunding(FundingType.FULLY_FUNDED);
    profile.setPreferredCategories(Set.of("تدريب"));
    profile.setPreferredCountries(Set.of("الأردن"));
    profile.setPreferredSpecialities(Set.of("علوم الحاسوب"));
    profile.setSkills(Set.of("Java"));
    profile.setInterests(Set.of("البرمجة"));
    return profile;
  }

  private Opportunity opportunity(Long id, String category, String country) {
    var opportunity = new Opportunity();
    opportunity.setId(id);
    opportunity.setCreatedAt(Instant.now());
    opportunity.setSlug("opportunity-" + id);
    opportunity.setTitle("فرصة " + id);
    opportunity.setShortDescription("وصف الفرصة");
    opportunity.setDescription("تفاصيل الفرصة");
    opportunity.setOrganization("جهة");
    opportunity.setCategory(category);
    opportunity.setCountry(country);
    opportunity.setFunding(FundingType.FULLY_FUNDED);
    opportunity.setPublished(true);
    opportunity.setDeadline(LocalDate.now().plusDays(30));
    return opportunity;
  }

  private CatalogItem job(Long id) {
    var job = new CatalogItem();
    job.setId(id);
    job.setCreatedAt(Instant.now());
    job.setType(CatalogType.JOB);
    job.setSlug("job-" + id);
    job.setTitle("وظيفة " + id);
    job.setSummary("وصف الوظيفة");
    job.setContent("تفاصيل الوظيفة");
    job.setCategory("تكنولوجيا");
    job.setOrganization("شركة");
    job.setCountry("الأردن");
    job.setPublished(true);
    return job;
  }
}
