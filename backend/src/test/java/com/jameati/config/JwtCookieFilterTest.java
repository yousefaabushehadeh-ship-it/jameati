package com.jameati.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.jameati.auth.*;
import com.jameati.user.*;
import jakarta.servlet.http.Cookie;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtCookieFilterTest {
  private final JwtService jwt = new JwtService("test-secret-that-is-at-least-thirty-two-bytes-long", 60_000);
  private final UserRepository users = mock(UserRepository.class);

  @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

  @Test
  void readsAccessCookieAndRejectsDisabledAccounts() throws Exception {
    var user = new User("editor@example.com", "hash", "Editor");
    user.getRoles().add(RoleName.DATA_ENTRY);
    var token = jwt.create(user);
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie(AuthController.ACCESS_COOKIE, token));
    when(users.findByEmailIgnoreCaseAndDeletedFalse(user.getEmail())).thenReturn(Optional.of(user));

    new JwtFilter(jwt, users).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(user.getEmail());

    SecurityContextHolder.clearContext();
    user.setEnabled(false);
    new JwtFilter(jwt, users).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
