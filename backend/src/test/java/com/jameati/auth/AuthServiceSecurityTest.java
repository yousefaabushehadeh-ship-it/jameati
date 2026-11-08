package com.jameati.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.jameati.user.*;
import com.jameati.email.EmailAccountService;
import com.jameati.settings.SiteSettingService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceSecurityTest {
  @Test
  void disabledUserCannotLoginEvenWithTheRightPassword() {
    var users = mock(UserRepository.class);
    var tokens = mock(RefreshTokenRepository.class);
    var encoder = mock(PasswordEncoder.class);
    var user = new User("disabled@example.com", "hash", "Disabled");
    user.setEnabled(false);
    when(users.findByEmailIgnoreCaseAndDeletedFalse("disabled@example.com")).thenReturn(Optional.of(user));
    when(encoder.matches("correct", "hash")).thenReturn(true);

    var service = new AuthService(users, tokens, encoder, mock(JwtService.class), mock(EmailAccountService.class), 60_000);

    assertThatThrownBy(() -> service.login(new AuthDtos.LoginRequest("disabled@example.com", "correct", false)))
        .isInstanceOf(BadCredentialsException.class);
    verify(encoder).matches("correct", "hash");
  }

  @Test
  void registrationSettingIsEnforcedByTheApiService() {
    var users = mock(UserRepository.class);
    var settings = mock(SiteSettingService.class);
    when(settings.publicBoolean("settings", "registrationEnabled", true)).thenReturn(false);
    var service = new AuthService(users, mock(RefreshTokenRepository.class), mock(PasswordEncoder.class),
        mock(JwtService.class), mock(EmailAccountService.class), settings, 60_000);

    assertThatThrownBy(() -> service.register(new AuthDtos.RegisterRequest("New User", "new@example.com", "password123")))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verify(users, never()).save(any());
  }
}
