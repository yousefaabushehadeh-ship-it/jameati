package com.jameati.opportunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jameati.subscription.ContentAccessService;
import com.jameati.subscription.SubscriptionPlanService;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OpportunityArabicOrderingIntegrationTest {
  @Autowired private OpportunityRepository repository;
  @Autowired private OpportunityMapper mapper;

  @Test
  void returnsAllArabicTitlesBeforeEnglishAcrossPagesAndKeepsRequestedSort() {
    var marker = "arabic-ordering-opportunity-unique";
    repository.saveAll(List.of(
        opportunity(marker + "-arabic-b", "منحة باء " + marker),
        opportunity(marker + "-english-b", "Zulu scholarship " + marker),
        opportunity(marker + "-arabic-a", "🎓 منحة ألف " + marker),
        opportunity(marker + "-english-a", "Upwork — بوابة " + marker)));
    repository.flush();

    var access = mock(ContentAccessService.class);
    var context = mock(ContentAccessService.AccessContext.class);
    when(access.resolve((Principal) null)).thenReturn(context);
    when(access.isOpportunityLocked(
        org.mockito.ArgumentMatchers.eq(context), org.mockito.ArgumentMatchers.anyLong()))
        .thenReturn(false);
    var service = new OpportunityService(
        repository, mapper, access, mock(SubscriptionPlanService.class), 14);
    var filter = new OpportunityDtos.Filter(
        marker, null, null, null, null, null, null, null);
    var requested = Sort.by(Sort.Order.asc("slug"));

    var firstPage = service.search(filter, PageRequest.of(0, 2, requested), null);
    var secondPage = service.search(filter, PageRequest.of(1, 2, requested), null);

    assertThat(firstPage.getContent()).extracting(OpportunityDtos.OpportunityResponse::slug)
        .containsExactly(marker + "-arabic-a", marker + "-arabic-b");
    assertThat(secondPage.getContent()).extracting(OpportunityDtos.OpportunityResponse::slug)
        .containsExactly(marker + "-english-a", marker + "-english-b");
    assertThat(firstPage.getTotalElements()).isEqualTo(4);
  }

  private static Opportunity opportunity(String slug, String title) {
    var opportunity = new Opportunity();
    opportunity.setSlug(slug);
    opportunity.setTitle(title);
    opportunity.setShortDescription("وصف قصير");
    opportunity.setDescription("وصف " + slug);
    opportunity.setCategory("منحة");
    opportunity.setOrganization("مؤسسة");
    opportunity.setCountry("الأردن");
    opportunity.setFunding(FundingType.FULLY_FUNDED);
    opportunity.setAlwaysOpen(true);
    opportunity.setPublished(true);
    return opportunity;
  }
}
