package com.jameati.opportunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.jameati.subscription.ContentAccessService;
import com.jameati.subscription.SubscriptionPlanService;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class OpportunityServiceTest {
  private final OpportunityService service=new OpportunityService(mock(OpportunityRepository.class),mock(OpportunityMapper.class),mock(ContentAccessService.class),mock(SubscriptionPlanService.class),14);
  @Test void computesAlwaysOpenBeforeDeadline(){var o=new Opportunity();o.setAlwaysOpen(true);o.setDeadline(LocalDate.now().minusDays(2));assertThat(service.status(o)).isEqualTo(OpportunityStatus.ALWAYS_OPEN);}
  @Test void computesExpired(){var o=new Opportunity();o.setDeadline(LocalDate.now().minusDays(1));assertThat(service.status(o)).isEqualTo(OpportunityStatus.EXPIRED);}
  @Test void computesClosingSoon(){var o=new Opportunity();o.setDeadline(LocalDate.now().plusDays(7));assertThat(service.status(o)).isEqualTo(OpportunityStatus.CLOSING_SOON);}
  @Test void computesOpen(){var o=new Opportunity();o.setDeadline(LocalDate.now().plusDays(30));assertThat(service.status(o)).isEqualTo(OpportunityStatus.OPEN);}
  @Test void usesEditorialStatusOverride(){var o=new Opportunity();o.setDeadline(LocalDate.now().plusDays(30));o.setStatusOverride(OpportunityStatus.UPCOMING);assertThat(service.status(o)).isEqualTo(OpportunityStatus.UPCOMING);}
  @Test void computesUnknown(){assertThat(service.status(new Opportunity())).isEqualTo(OpportunityStatus.UNKNOWN);}

  @Test
  void featuredUsesArabicFirstThenLatestWithStableTieBreak() {
    var repository = mock(OpportunityRepository.class);
    var access = mock(ContentAccessService.class);
    when(repository.featured(any())).thenReturn(List.of());

    new OpportunityService(
        repository, mock(OpportunityMapper.class), access,
        mock(SubscriptionPlanService.class), 14)
        .featured(6, null);

    verify(repository).featured(argThat(pageable -> {
      var orders = pageable.getSort().toList();
      return orders.size() == 3
          && orders.get(0).getProperty().equals("arabicTitle")
          && orders.get(0).getDirection().isDescending()
          && orders.get(1).getProperty().equals("createdAt")
          && orders.get(1).getDirection().isDescending()
          && orders.get(2).getProperty().equals("id")
          && orders.get(2).getDirection().isDescending();
    }));
  }

  @Test
  void lockedDetailKeepsOnlyPreviewFields() {
    var repository = mock(OpportunityRepository.class);
    var mapper = mock(OpportunityMapper.class);
    var access = mock(ContentAccessService.class);
    var context = mock(ContentAccessService.AccessContext.class);
    var opportunity = new Opportunity();
    opportunity.setId(42L);
    opportunity.setSlug("locked-opportunity");
    when(repository.findBySlugAndDeletedFalseAndPublishedTrue("locked-opportunity"))
        .thenReturn(Optional.of(opportunity));
    when(access.resolve((Principal) null)).thenReturn(context);
    when(access.isOpportunityLocked(context, 42L)).thenReturn(true);
    when(mapper.toResponse(opportunity)).thenReturn(fullResponse(42L));

    var response = new OpportunityService(repository, mapper, access, mock(SubscriptionPlanService.class), 14)
        .detail("locked-opportunity", null);

    assertThat(response.title()).isEqualTo("عنوان تجريبي");
    assertThat(response.shortDescription()).isEqualTo("معاينة قصيرة");
    assertThat(response.description()).isNull();
    assertThat(response.officialUrl()).isNull();
    assertThat(response.applyUrl()).isNull();
    assertThat(response.sourceName()).isNull();
    assertThat(response.sourceUrl()).isNull();
    assertThat(response.lastVerifiedAt()).isNull();
    assertThat(response.serviceEnabled()).isFalse();
    assertThat(response.servicePrice()).isNull();
    assertThat(response.specialities()).isEmpty();
    assertThat(response.nationalities()).isEmpty();
    assertThat(response.degreeLevels()).isEmpty();
    assertThat(response.accessLocked()).isTrue();
  }

  @Test
  void homepageLoadsConfiguredOldSelectionsWithoutPagingThroughTheCatalog() {
    var repository = mock(OpportunityRepository.class);
    var mapper = mock(OpportunityMapper.class);
    var access = mock(ContentAccessService.class);
    var plan = new SubscriptionPlanService.PlanConfig(
        true,
        BigDecimal.TEN,
        "USD",
        12,
        4,
        Map.of(SubscriptionPlanService.OPPORTUNITY, List.of(2L, 100L)),
        "");
    var context = new ContentAccessService.AccessContext(false, false, null, plan);
    var selectedOld = opportunity(2L);
    var latestSelected = opportunity(100L);
    var latestSecond = opportunity(99L);
    var latestThird = opportunity(98L);
    var latestFourth = opportunity(97L);
    latestSelected.setArabicTitle(true);
    latestSecond.setArabicTitle(true);

    when(access.resolve((Principal) null)).thenReturn(context);
    when(repository.findByIdInAndPublishedTrueAndDeletedFalse(List.of(2L, 100L)))
        .thenReturn(List.of(latestSelected, selectedOld));
    when(repository.findByPublishedTrueAndDeletedFalse(any()))
        .thenReturn(new PageImpl<>(
            List.of(latestSelected, latestSecond, latestThird, latestFourth),
            PageRequest.of(0, 4),
            12_531));
    for (var item : List.of(selectedOld, latestSelected, latestSecond, latestThird, latestFourth)) {
      when(mapper.toResponse(item)).thenReturn(fullResponse(item.getId()));
    }

    var result = new OpportunityService(
        repository, mapper, access, mock(SubscriptionPlanService.class), 14)
        .homepage(4, null);

    assertThat(result.getTotalElements()).isEqualTo(12_531);
    assertThat(result.getContent()).extracting(OpportunityDtos.OpportunityResponse::id)
        .containsExactly(100L, 99L, 2L, 98L);
    verify(repository, times(1)).findByIdInAndPublishedTrueAndDeletedFalse(List.of(2L, 100L));
    verify(repository, times(1)).findByPublishedTrueAndDeletedFalse(argThat(pageable ->
        pageable.getPageNumber() == 0
            && pageable.getPageSize() == 4
            && pageable.getSort().toList().getFirst().getProperty().equals("arabicTitle")));
  }

  private static Opportunity opportunity(Long id) {
    var opportunity = new Opportunity();
    opportunity.setId(id);
    return opportunity;
  }

  private static OpportunityDtos.OpportunityResponse fullResponse(Long id) {
    var now = Instant.now();
    return new OpportunityDtos.OpportunityResponse(
        id, "opportunity-" + id, "عنوان تجريبي", "معاينة قصيرة", "تفاصيل سرية",
        "منحة", "مؤسسة", "فلسطين", FundingType.FULLY_FUNDED, null, "مفتوح", true,
        true, true, "cover.jpg", "https://official.example", "https://apply.example", true,
        BigDecimal.TEN, "USD", "المصدر", "https://source.example", now, 30, 8,
        Set.of("هندسة"), Set.of("ALL"), Set.of("BACHELOR"), OpportunityStatus.ALWAYS_OPEN,
        0, now, now, false, null, null);
  }
}
