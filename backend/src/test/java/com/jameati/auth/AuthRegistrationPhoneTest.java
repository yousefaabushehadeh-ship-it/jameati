package com.jameati.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jameati.email.EmailAccountService;
import com.jameati.me.UserProfile;
import com.jameati.me.UserProfileRepository;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthRegistrationPhoneTest {
  @Test
  void registrationPersistsANormalizedPhoneInTheUserProfile() {
    var users = mock(UserRepository.class);
    var profiles = mock(UserProfileRepository.class);
    var tokens = mock(RefreshTokenRepository.class);
    var encoder = mock(PasswordEncoder.class);
    var jwt = mock(JwtService.class);
    var emailAccounts = mock(EmailAccountService.class);
    when(users.save(any(User.class)))
        .thenAnswer(
            invocation -> {
              var user = invocation.getArgument(0, User.class);
              user.setId(42L);
              return user;
            });
    when(encoder.encode("national-number")).thenReturn("encoded-national-number");
    when(jwt.create(any(User.class))).thenReturn("access-token");
    when(jwt.expirationSeconds()).thenReturn(900L);

    var service =
        new AuthService(
            users, tokens, encoder, jwt, emailAccounts, profiles, null, 2_592_000L);

    service.register(
        new AuthDtos.RegisterRequest(
            "  طالب جديد  ",
            "  STUDENT@example.com  ",
            " +962 (79) 123-4567 ",
            "national-number"));

    var userCaptor = ArgumentCaptor.forClass(User.class);
    verify(users).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getEmail()).isEqualTo("student@example.com");
    assertThat(userCaptor.getValue().getFullName()).isEqualTo("طالب جديد");

    var profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
    verify(profiles).save(profileCaptor.capture());
    assertThat(profileCaptor.getValue().getUser()).isSameAs(userCaptor.getValue());
    assertThat(profileCaptor.getValue().getPhone()).isEqualTo("+962791234567");
    verify(emailAccounts).beginVerification(userCaptor.getValue());
  }

  @Test
  void registrationRejectsMalformedPhoneBeforePersistingTheUser() {
    var users = mock(UserRepository.class);
    var profiles = mock(UserProfileRepository.class);
    var service =
        new AuthService(
            users,
            mock(RefreshTokenRepository.class),
            mock(PasswordEncoder.class),
            mock(JwtService.class),
            mock(EmailAccountService.class),
            profiles,
            null,
            2_592_000L);

    assertThatThrownBy(
            () ->
                service.register(
                    new AuthDtos.RegisterRequest(
                        "طالب", "student@example.com", "not-a-phone", "national-number")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("رقم الهاتف غير صالح");

    verify(users, never()).save(any());
    verify(profiles, never()).save(any());
  }

  @Test
  void normalizesArabicDigitsWithoutLosingTheInternationalPrefix() {
    assertThat(AuthService.normalizePhone("+٩٦٢ ٧٩ ١٢٣ ٤٥٦٧"))
        .isEqualTo("+962791234567");
  }
}
