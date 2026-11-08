package com.jameati.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class EmailAccountDtos {
  private EmailAccountDtos() {}

  public record EmailRequest(@NotBlank @Email @Size(max = 190) String email) {}

  public record TokenRequest(@NotBlank String token) {}

  public record ResetPasswordRequest(
      @NotBlank String token, @NotBlank @Size(min = 8, max = 72) String password) {}

  public record ActionMessage(String message) {}
}
