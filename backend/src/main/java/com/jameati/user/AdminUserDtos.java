package com.jameati.user;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Set;

public final class AdminUserDtos {
  private AdminUserDtos() {}

  public record UserResponse(
      Long id,
      String fullName,
      String email,
      boolean enabled,
      Instant emailVerifiedAt,
      Set<RoleName> roles,
      Instant createdAt,
      Instant updatedAt) {
    static UserResponse of(User user) {
      return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.isEnabled(),
          user.getEmailVerifiedAt(), Set.copyOf(user.getRoles()), user.getCreatedAt(), user.getUpdatedAt());
    }
  }

  public record StatusRequest(@NotNull Boolean enabled) {}
  public record RolesRequest(@NotEmpty Set<@NotNull RoleName> roles) {}
}
