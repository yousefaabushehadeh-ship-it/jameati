package com.jameati.ai;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jameati.me.CurrentUserService;
import com.jameati.recommendation.RecommendationDtos;
import com.jameati.recommendation.RecommendationService;
import com.jameati.subscription.FeatureAccessService;
import com.jameati.user.User;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiRecommendationControllerTest {
  @Test
  void returnsNoStoreEnvelopeAndDelegatesOnlyForCurrentUser() throws Exception {
    var currentUsers = mock(CurrentUserService.class);
    var service = mock(RecommendationService.class);
    var user = new User("student@example.com", "hash", "طالب");
    user.setId(3L);
    var bundle = new RecommendationDtos.RecommendationBundle(
        true, 70, new FeatureAccessService.AccessSummary("FREE", 3, false, false, false, false),
        List.of(), 8, 3, 5, true);
    when(currentUsers.require(org.mockito.ArgumentMatchers.any(Principal.class))).thenReturn(user);
    when(service.recommendations(user)).thenReturn(bundle);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(
        new AiRecommendationController(currentUsers, service)).build();

    mvc.perform(get("/api/v1/me/ai-recommendations?limit=7")
            .principal(() -> "student@example.com"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.profileCompletionPercentage").value(70))
        .andExpect(jsonPath("$.data.subscription.recommendationLimit").value(3))
        .andExpect(jsonPath("$.data.recommendations.length()").value(0));
    verify(service).recommendations(user);
  }
}
