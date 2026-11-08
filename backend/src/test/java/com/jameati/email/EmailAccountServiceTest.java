package com.jameati.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jameati.auth.RefreshToken;
import com.jameati.auth.RefreshTokenRepository;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class EmailAccountServiceTest {
  private UserRepository users;
  private EmailTokenRepository tokens;
  private RefreshTokenRepository refreshTokens;
  private PasswordEncoder encoder;
  private EmailDeliveryGateway delivery;
  private EmailAccountService service;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    tokens = mock(EmailTokenRepository.class);
    refreshTokens = mock(RefreshTokenRepository.class);
    encoder = mock(PasswordEncoder.class);
    delivery = mock(EmailDeliveryGateway.class);
    service =
        new EmailAccountService(
            users, tokens, refreshTokens, encoder, delivery, 1440, 30, "https://jameati.test");
  }

  @Test
  void forgotPasswordReturnsGenericMessageAndStoresOnlyAHash() {
    var user = user();
    when(users.findByEmailIgnoreCaseAndDeletedFalse("student@example.com"))
        .thenReturn(Optional.of(user));
    when(tokens.findByUserIdAndTokenTypeAndUsedAtIsNullAndDeletedFalse(
            9L, EmailTokenType.PASSWORD_RESET))
        .thenReturn(List.of());

    var result = service.forgotPassword("student@example.com");

    var tokenCaptor = ArgumentCaptor.forClass(EmailToken.class);
    verify(tokens).save(tokenCaptor.capture());
    assertThat(tokenCaptor.getValue().getTokenHash()).hasSize(64);
    assertThat(result.message()).contains("إذا كان البريد مسجّلًا");
    verify(delivery).send(any());
  }

  @Test
  void resetPasswordConsumesTokenAndRevokesExistingSessions() throws Exception {
    var user = user();
    var token = new EmailToken();
    token.setId(4L);
    token.setUser(user);
    token.setTokenType(EmailTokenType.PASSWORD_RESET);
    token.setTokenHash(hash("raw-reset-token"));
    token.setExpiresAt(Instant.now().plusSeconds(300));
    var refresh = new RefreshToken();
    refresh.setUser(user);
    refresh.setExpiresAt(Instant.now().plusSeconds(300));
    when(tokens.findByTokenHashAndTokenTypeAndUsedAtIsNullAndDeletedFalse(
            hash("raw-reset-token"), EmailTokenType.PASSWORD_RESET))
        .thenReturn(Optional.of(token));
    when(tokens.findByUserIdAndTokenTypeAndUsedAtIsNullAndDeletedFalse(
            9L, EmailTokenType.PASSWORD_RESET))
        .thenReturn(List.of(token));
    when(refreshTokens.findByUserIdAndRevokedAtIsNullAndDeletedFalse(9L))
        .thenReturn(List.of(refresh));
    when(encoder.encode("NewPassword123!")).thenReturn("new-hash");

    service.resetPassword("raw-reset-token", "NewPassword123!");

    assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    assertThat(token.getUsedAt()).isNotNull();
    assertThat(refresh.getRevokedAt()).isNotNull();
  }

  private User user() {
    var user = new User("student@example.com", "old-hash", "طالب");
    user.setId(9L);
    return user;
  }

  private static String hash(String raw) throws Exception {
    return HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
  }
}
