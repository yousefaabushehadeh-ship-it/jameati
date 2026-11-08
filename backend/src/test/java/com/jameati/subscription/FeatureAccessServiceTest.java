package com.jameati.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jameati.user.User;
import org.junit.jupiter.api.Test;

class FeatureAccessServiceTest {
  @Test
  void centralizesFreeAndPremiumRecommendationEntitlements() {
    var contentAccess = mock(ContentAccessService.class);
    var free = user(1L);
    var premium = user(2L);
    when(contentAccess.resolve(free)).thenReturn(
        new ContentAccessService.AccessContext(true, false, null, null));
    when(contentAccess.resolve(premium)).thenReturn(
        new ContentAccessService.AccessContext(true, true, null, null));
    var service = new FeatureAccessService(contentAccess);

    assertThat(service.hasAccess(free, Feature.PERSONALIZED_RECOMMENDATIONS)).isTrue();
    assertThat(service.getRecommendationLimit(free)).isEqualTo(3);
    assertThat(service.hasAccess(free, Feature.UNLIMITED_RECOMMENDATIONS)).isFalse();
    assertThat(service.getRecommendationLimit(premium)).isEqualTo(Integer.MAX_VALUE);
    assertThat(service.summary(premium).unlimitedRecommendations()).isTrue();
  }

  private static User user(Long id) {
    var user = new User("user" + id + "@example.com", "hash", "User");
    user.setId(id);
    return user;
  }
}
