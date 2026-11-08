package com.jameati.recommendation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jameati.me.CurrentUserService;
import com.jameati.subscription.FeatureAccessService;
import com.jameati.user.User;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecommendationControllerTest {
  @Test
  void limitQueryParameterCannotBypassBackendEntitlement() throws Exception {
    var currentUsers = mock(CurrentUserService.class);
    var recommendations = mock(RecommendationService.class);
    var features = mock(FeatureAccessService.class);
    var user = new User("student@example.com", "hash", "Student");
    user.setId(5L);
    when(currentUsers.require(any(Principal.class))).thenReturn(user);
    when(recommendations.recommendations(user)).thenReturn(new RecommendationDtos.RecommendationBundle(
        true, 100,
        new FeatureAccessService.AccessSummary("FREE", 3, false, false, false, false),
        List.of(), 9, 3, 6, true));
    var mvc = MockMvcBuilders.standaloneSetup(
        new RecommendationController(currentUsers, recommendations, features)).build();

    mvc.perform(get("/api/v1/me/recommendations?limit=9999")
            .principal(() -> "student@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.subscription.recommendationLimit").value(3))
        .andExpect(jsonPath("$.data.visibleMatches").value(3))
        .andExpect(jsonPath("$.data.lockedMatches").value(6));
    verify(recommendations).recommendations(user);
  }
}
