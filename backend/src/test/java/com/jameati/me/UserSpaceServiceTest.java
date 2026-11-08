package com.jameati.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.catalog.CatalogRepository;
import com.jameati.opportunity.FundingType;
import com.jameati.opportunity.Opportunity;
import com.jameati.opportunity.OpportunityRepository;
import com.jameati.user.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class UserSpaceServiceTest {
  private UserProfileRepository profiles;
  private OpportunityRepository opportunities;
  private UserSpaceService service;

  @BeforeEach
  void setUp() {
    profiles = mock(UserProfileRepository.class);
    opportunities = mock(OpportunityRepository.class);
    service =
        new UserSpaceService(
            profiles,
            mock(UserApplicationRepository.class),
            mock(UserNotificationRepository.class),
            mock(FollowedOrganizationRepository.class),
            mock(RecentlyViewedOpportunityRepository.class),
            mock(CvDocumentRepository.class),
            mock(PersonalityResultRepository.class),
            opportunities,
            mock(CatalogRepository.class),
            new ObjectMapper());
  }

  @Test
  @SuppressWarnings("unchecked")
  void recommendationsPutStrongProfileMatchesFirst() {
    var user = new User("student@example.com", "hash", "طالب");
    user.setId(7L);
    var profile = new UserProfile();
    profile.setUser(user);
    profile.setPreferredCategories(Set.of("تدريب"));
    profile.setPreferredCountries(Set.of("كندا"));
    profile.setPreferredSpecialities(Set.of("علوم الحاسوب"));
    profile.setDegreeLevel("بكالوريوس");
    profile.setPreferredFunding(FundingType.PAID);

    var generic = opportunity(1L, "منحة", "ألمانيا", FundingType.FULLY_FUNDED);
    var matching = opportunity(2L, "تدريب", "كندا", FundingType.PAID);
    matching.setSpecialities(Set.of("علوم الحاسوب"));
    matching.setDegreeLevels(Set.of("بكالوريوس"));
    matching.setNationalities(Set.of("ALL"));

    when(profiles.findByUserIdAndDeletedFalse(7L)).thenReturn(Optional.of(profile));
    when(opportunities.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(generic, matching)));

    var result = service.recommendations(user, 10);

    assertThat(result).hasSize(2);
    assertThat(result.getFirst().opportunity().id()).isEqualTo(2L);
    assertThat(result.getFirst().score()).isGreaterThan(result.getLast().score());
    assertThat(result.getFirst().reasons()).contains("متوافق مع تخصصك");
  }

  private Opportunity opportunity(
      Long id, String category, String country, FundingType funding) {
    var opportunity = new Opportunity();
    opportunity.setId(id);
    opportunity.setSlug("opportunity-" + id);
    opportunity.setTitle("فرصة " + id);
    opportunity.setShortDescription("وصف");
    opportunity.setDescription("تفاصيل");
    opportunity.setOrganization("جهة");
    opportunity.setCategory(category);
    opportunity.setCountry(country);
    opportunity.setFunding(funding);
    opportunity.setPublished(true);
    opportunity.setDeadline(LocalDate.now().plusDays(30));
    return opportunity;
  }
}
