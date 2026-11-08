package com.jameati.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.me.UserProfile;
import com.jameati.me.UserProfileRepository;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class OnboardingRequiredFilterTest {
  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void blocksProtectedMeEndpointUntilOnboardingIsComplete() throws Exception {
    var users = mock(UserRepository.class);
    var profiles = mock(UserProfileRepository.class);
    var user = new User("student@example.com", "hash", "طالب");
    user.setId(7L);
    var profile = new UserProfile();
    profile.setUser(user);
    when(users.findByEmailIgnoreCaseAndDeletedFalse(user.getEmail())).thenReturn(Optional.of(user));
    when(profiles.findByUserIdAndDeletedFalse(7L)).thenReturn(Optional.of(profile));
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(user.getEmail(), null, java.util.List.of()));
    var filter = new OnboardingRequiredFilter(users, profiles, new ObjectMapper().findAndRegisterModules());
    var request = new MockHttpServletRequest("GET", "/api/v1/me/applications");
    request.setRequestURI("/api/v1/me/applications");
    var response = new MockHttpServletResponse();
    var chain = mock(jakarta.servlet.FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("ONBOARDING_REQUIRED");
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void permitsProtectedMeEndpointAfterCompletion() throws Exception {
    var users = mock(UserRepository.class);
    var profiles = mock(UserProfileRepository.class);
    var user = new User("student@example.com", "hash", "طالب");
    user.setId(7L);
    var profile = new UserProfile();
    profile.setUser(user);
    profile.setOnboardingCompleted(true);
    when(users.findByEmailIgnoreCaseAndDeletedFalse(user.getEmail())).thenReturn(Optional.of(user));
    when(profiles.findByUserIdAndDeletedFalse(7L)).thenReturn(Optional.of(profile));
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(user.getEmail(), null, java.util.List.of()));
    var filter = new OnboardingRequiredFilter(users, profiles, new ObjectMapper().findAndRegisterModules());
    var request = new MockHttpServletRequest("GET", "/api/v1/me/applications");
    request.setRequestURI("/api/v1/me/applications");
    var response = new MockHttpServletResponse();
    var chain = mock(jakarta.servlet.FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }
}
