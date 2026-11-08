package com.jameati.auth;

import jakarta.validation.constraints.*;
import java.util.Set;

public final class AuthDtos {
  private AuthDtos() {}
  public record RegisterRequest(
      @NotBlank @Size(max = 120) String fullName,
      @NotBlank @Email @Size(max = 190) String email,
      @NotBlank
          @Size(max = 40)
          @Pattern(
              regexp = "^\\s*\\+?[0-9\u0660-\u0669\u06F0-\u06F9][0-9\u0660-\u0669\u06F0-\u06F9 .()\\-]{5,38}\\s*$",
              message = "رقم الهاتف غير صالح")
          String phone,
      @NotBlank @Size(min = 8, max = 72) String password) {
    /** Keeps source compatibility for internal callers while HTTP registration requires a phone. */
    public RegisterRequest(String fullName, String email, String password) {
      this(fullName, email, null, password);
    }
  }
  public record LoginRequest(@NotBlank @Email @Size(max=190) String email, @NotBlank @Size(max=72) String password, boolean rememberMe) {}
  public record RefreshRequest(@NotBlank @Size(max=512) String refreshToken) {}
  public record AuthResponse(String accessToken, String refreshToken, long expiresIn, UserSummary user) {}
  public record UserSummary(Long id, String fullName, String email, Set<String> roles) {}
}
