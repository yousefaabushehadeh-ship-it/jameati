package com.jameati.email;

import static com.jameati.email.EmailAccountDtos.ActionMessage;
import static com.jameati.email.EmailDeliveryGateway.EmailMessage;

import com.jameati.auth.RefreshTokenRepository;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmailAccountService {
  private static final Logger LOGGER = LoggerFactory.getLogger(EmailAccountService.class);
  private static final String GENERIC_MESSAGE =
      "إذا كان البريد مسجّلًا فستصلك رسالة تحتوي على الخطوات التالية.";

  private final UserRepository users;
  private final EmailTokenRepository tokens;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final EmailDeliveryGateway delivery;
  private final SecureRandom random = new SecureRandom();
  private final Duration verificationTtl;
  private final Duration resetTtl;
  private final String frontendUrl;

  public EmailAccountService(
      UserRepository users,
      EmailTokenRepository tokens,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder,
      EmailDeliveryGateway delivery,
      @Value("${app.mail.verification-token-minutes:1440}") long verificationMinutes,
      @Value("${app.mail.reset-token-minutes:30}") long resetMinutes,
      @Value("${app.mail.frontend-url:http://localhost:3000}") String frontendUrl) {
    this.users = users;
    this.tokens = tokens;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.delivery = delivery;
    this.verificationTtl = Duration.ofMinutes(verificationMinutes);
    this.resetTtl = Duration.ofMinutes(resetMinutes);
    this.frontendUrl = frontendUrl.replaceAll("/+$", "");
  }

  public void beginVerification(User user) {
    if (user.getEmailVerifiedAt() != null) {
      return;
    }
    var raw = issue(user, EmailTokenType.VERIFY_EMAIL, verificationTtl);
    deliver(
        new EmailMessage(
            user.getEmail(),
            "تأكيد بريدك في جامعتي",
            "<p>مرحبًا "
                + escape(user.getFullName())
                + "،</p><p>أكد بريدك عبر الرابط:</p><p><a href=\""
                + frontendUrl
                + "/verify-email?token="
                + raw
                + "\">تأكيد البريد</a></p>",
            true));
  }

  public ActionMessage resendVerification(String email) {
    users
        .findByEmailIgnoreCaseAndDeletedFalse(email.trim())
        .filter(User::isEnabled)
        .ifPresent(this::beginVerification);
    return new ActionMessage(GENERIC_MESSAGE);
  }

  public ActionMessage forgotPassword(String email) {
    users
        .findByEmailIgnoreCaseAndDeletedFalse(email.trim())
        .filter(User::isEnabled)
        .ifPresent(
            user -> {
              var raw = issue(user, EmailTokenType.PASSWORD_RESET, resetTtl);
              deliver(
                  new EmailMessage(
                      user.getEmail(),
                      "إعادة تعيين كلمة مرور جامعتي",
                      "<p>استخدم الرابط الآمن لإعادة تعيين كلمة المرور:</p><p><a href=\""
                          + frontendUrl
                          + "/reset-password?token="
                          + raw
                          + "\">إعادة تعيين كلمة المرور</a></p>",
                      true));
            });
    return new ActionMessage(GENERIC_MESSAGE);
  }

  public ActionMessage verifyEmail(String rawToken) {
    var token = active(rawToken, EmailTokenType.VERIFY_EMAIL);
    token.getUser().setEmailVerifiedAt(Instant.now());
    token.setUsedAt(Instant.now());
    invalidate(token.getUser(), EmailTokenType.VERIFY_EMAIL, token.getId());
    return new ActionMessage("تم تأكيد البريد الإلكتروني بنجاح.");
  }

  public ActionMessage resetPassword(String rawToken, String password) {
    var token = active(rawToken, EmailTokenType.PASSWORD_RESET);
    var user = token.getUser();
    user.setPasswordHash(passwordEncoder.encode(password));
    token.setUsedAt(Instant.now());
    invalidate(user, EmailTokenType.PASSWORD_RESET, token.getId());
    var now = Instant.now();
    refreshTokens
        .findByUserIdAndRevokedAtIsNullAndDeletedFalse(user.getId())
        .forEach(refreshToken -> refreshToken.setRevokedAt(now));
    return new ActionMessage("تم تحديث كلمة المرور. يمكنك تسجيل الدخول الآن.");
  }

  private String issue(User user, EmailTokenType type, Duration ttl) {
    invalidate(user, type, null);
    var bytes = new byte[32];
    random.nextBytes(bytes);
    var raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var token = new EmailToken();
    token.setUser(user);
    token.setTokenHash(hash(raw));
    token.setTokenType(type);
    token.setExpiresAt(Instant.now().plus(ttl));
    tokens.save(token);
    return raw;
  }

  private EmailToken active(String rawToken, EmailTokenType type) {
    var token =
        tokens
            .findByTokenHashAndTokenTypeAndUsedAtIsNullAndDeletedFalse(
                hash(rawToken), type)
            .filter(candidate -> candidate.getExpiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> new IllegalArgumentException("الرابط غير صالح أو منتهي الصلاحية"));
    if (!token.getUser().isEnabled() || token.getUser().isDeleted()) {
      throw new IllegalArgumentException("الرابط غير صالح أو منتهي الصلاحية");
    }
    return token;
  }

  private void invalidate(User user, EmailTokenType type, Long exceptId) {
    var now = Instant.now();
    tokens
        .findByUserIdAndTokenTypeAndUsedAtIsNullAndDeletedFalse(user.getId(), type).stream()
        .filter(token -> exceptId == null || !exceptId.equals(token.getId()))
        .forEach(token -> token.setUsedAt(now));
  }

  private static String escape(String value) {
    return value == null
        ? ""
        : value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
  }

  private static String hash(String value) {
    try {
      var digest =
          MessageDigest.getInstance("SHA-256")
              .digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception exception) {
      throw new IllegalStateException("تعذر تجهيز رمز البريد", exception);
    }
  }

  private void deliver(EmailMessage message) {
    try {
      delivery.send(message);
    } catch (RuntimeException exception) {
      LOGGER.error(
          "تعذر تسليم رسالة حساب إلى {} عبر بوابة البريد المهيأة",
          message.recipient(),
          exception);
    }
  }
}
