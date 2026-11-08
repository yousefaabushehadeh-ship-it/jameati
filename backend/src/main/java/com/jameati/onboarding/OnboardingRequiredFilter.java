package com.jameati.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.common.ApiResponse;
import com.jameati.me.UserProfileRepository;
import com.jameati.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OnboardingRequiredFilter extends OncePerRequestFilter {
  private static final String ME_PREFIX = "/api/v1/me/";
  private final UserRepository users;
  private final UserProfileRepository profiles;
  private final ObjectMapper objectMapper;

  public OnboardingRequiredFilter(
      UserRepository users, UserProfileRepository profiles, ObjectMapper objectMapper) {
    this.users = users;
    this.profiles = profiles;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    var path = request.getRequestURI();
    return !path.startsWith(ME_PREFIX)
        || path.startsWith(ME_PREFIX + "onboarding")
        || path.equals(ME_PREFIX + "profile")
        || path.startsWith(ME_PREFIX + "memberships")
        || path.startsWith(ME_PREFIX + "scholarship-subscriptions");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      var complete = users.findByEmailIgnoreCaseAndDeletedFalse(authentication.getName())
          .flatMap(user -> profiles.findByUserIdAndDeletedFalse(user.getId()))
          .map(profile -> profile.isOnboardingCompleted())
          .orElse(false);
      if (!complete) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error("ONBOARDING_REQUIRED"));
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
