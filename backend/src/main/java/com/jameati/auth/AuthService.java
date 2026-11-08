package com.jameati.auth;

import static com.jameati.auth.AuthDtos.*;
import com.jameati.crm.CrmService;
import com.jameati.email.EmailAccountService;
import com.jameati.me.UserProfile;
import com.jameati.me.UserProfileRepository;
import com.jameati.settings.SiteSettingService;
import com.jameati.user.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class AuthService {
  private final UserRepository users; private final RefreshTokenRepository tokens; private final PasswordEncoder encoder; private final JwtService jwt; private final EmailAccountService emailAccounts; private final UserProfileRepository profiles; private final SiteSettingService siteSettings; private final CrmService crm; private final long refreshMs;
  @Autowired
  public AuthService(UserRepository users, RefreshTokenRepository tokens, PasswordEncoder encoder, JwtService jwt, EmailAccountService emailAccounts, UserProfileRepository profiles, SiteSettingService siteSettings, CrmService crm, @Value("${app.jwt.refresh-expiration-ms}") long refreshMs) { this.users=users;this.tokens=tokens;this.encoder=encoder;this.jwt=jwt;this.emailAccounts=emailAccounts;this.profiles=profiles;this.siteSettings=siteSettings;this.crm=crm;this.refreshMs=refreshMs; }
  AuthService(UserRepository users, RefreshTokenRepository tokens, PasswordEncoder encoder, JwtService jwt, EmailAccountService emailAccounts, UserProfileRepository profiles, SiteSettingService siteSettings, long refreshMs) { this(users,tokens,encoder,jwt,emailAccounts,profiles,siteSettings,null,refreshMs); }
  AuthService(UserRepository users, RefreshTokenRepository tokens, PasswordEncoder encoder, JwtService jwt, EmailAccountService emailAccounts, SiteSettingService siteSettings, long refreshMs) { this(users,tokens,encoder,jwt,emailAccounts,null,siteSettings,null,refreshMs); }
  AuthService(UserRepository users, RefreshTokenRepository tokens, PasswordEncoder encoder, JwtService jwt, EmailAccountService emailAccounts, long refreshMs) { this(users,tokens,encoder,jwt,emailAccounts,null,null,null,refreshMs); }
  AuthService(UserRepository users, RefreshTokenRepository tokens, PasswordEncoder encoder, JwtService jwt, long refreshMs) { this(users,tokens,encoder,jwt,null,null,null,null,refreshMs); }
  public AuthResponse register(RegisterRequest request) {
    if(siteSettings!=null && !siteSettings.publicBoolean("settings","registrationEnabled",true)) throw new org.springframework.security.access.AccessDeniedException("التسجيل مغلق حاليًا");
    if(users.existsByEmailIgnoreCase(request.email())) throw new IllegalArgumentException("البريد الإلكتروني مستخدم بالفعل");
    var phone=normalizePhone(request.phone());
    var user=users.save(new User(request.email().trim().toLowerCase(), encoder.encode(request.password()), request.fullName().trim()));
    if(profiles!=null) {
      var profile=new UserProfile();
      profile.setUser(user);
      profile.setPhone(phone);
      profiles.save(profile);
    }
    if(crm!=null) crm.ensureLeadForRegistration(user, phone);
    if(emailAccounts!=null) emailAccounts.beginVerification(user);
    return issue(user);
  }
  public AuthResponse login(LoginRequest request) {
    var user=users.findByEmailIgnoreCaseAndDeletedFalse(request.email()).orElseThrow(() -> new BadCredentialsException("بيانات الدخول غير صحيحة"));
    var passwordMatches=encoder.matches(request.password(),user.getPasswordHash());
    if(!user.isEnabled() || !passwordMatches) throw new BadCredentialsException("بيانات الدخول غير صحيحة");
    return issue(user);
  }
  public AuthResponse refresh(String raw) {
    if(raw==null||raw.isBlank()||raw.length()>512)throw new BadCredentialsException("جلسة التحديث غير صالحة");
    var current=tokens.findByTokenHashAndRevokedAtIsNullAndDeletedFalse(hash(raw)).filter(t -> t.getExpiresAt().isAfter(Instant.now())).orElseThrow(() -> new BadCredentialsException("جلسة التحديث غير صالحة"));
    if(!current.getUser().isEnabled() || current.getUser().isDeleted())throw new BadCredentialsException("جلسة التحديث غير صالحة");
    current.setRevokedAt(Instant.now()); return issue(current.getUser());
  }
  public void logout(String raw) {
    if(raw==null||raw.isBlank()||raw.length()>512)return;
    tokens.findByTokenHashAndRevokedAtIsNullAndDeletedFalse(hash(raw)).ifPresent(token -> token.setRevokedAt(Instant.now()));
  }
  public UserSummary session(String email){var user=users.findByEmailIgnoreCaseAndDeletedFalse(email).filter(User::isEnabled).orElseThrow(()->new BadCredentialsException("الجلسة غير صالحة"));return summary(user);}
  private AuthResponse issue(User user) {
    if(!user.isEnabled()||user.isDeleted())throw new BadCredentialsException("الحساب غير متاح");
    var raw=UUID.randomUUID()+"."+UUID.randomUUID(); var token=new RefreshToken(); token.setTokenHash(hash(raw));token.setUser(user);token.setExpiresAt(Instant.now().plusMillis(refreshMs));tokens.save(token);
    return new AuthResponse(jwt.create(user),raw,jwt.expirationSeconds(),summary(user));
  }
  private static UserSummary summary(User user){var roles=user.getRoles().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet());return new UserSummary(user.getId(),user.getFullName(),user.getEmail(),roles);}
  static String normalizePhone(String value) {
    if(value==null||value.isBlank()) throw new IllegalArgumentException("رقم الهاتف مطلوب");
    var normalized=new StringBuilder();
    var digits=0;
    for(int index=0;index<value.length();index++) {
      var character=value.charAt(index);
      if(character=='+'&&normalized.isEmpty()) { normalized.append(character); continue; }
      var digit=Character.digit(character,10);
      if(digit>=0) { normalized.append(digit); digits++; continue; }
      if(Character.isWhitespace(character)||character=='-'||character=='.'||character=='('||character==')') continue;
      throw new IllegalArgumentException("رقم الهاتف غير صالح");
    }
    if(digits<7||digits>15) throw new IllegalArgumentException("رقم الهاتف غير صالح");
    return normalized.toString();
  }
  static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch(Exception e) { throw new IllegalStateException(e); } }
}
