package com.jameati.admin;

import org.springframework.security.core.Authentication;

public final class AdminAuthorization {
  private AdminAuthorization() {}

  public static boolean isDataEntryOnly(Authentication authentication) {
    if (authentication == null) return false;
    var roles = authentication.getAuthorities().stream().map(Object::toString).toList();
    return (roles.contains("ROLE_DATA_ENTRY") || roles.contains("ROLE_EDITOR"))
        && !roles.contains("ROLE_ADMIN")
        && !roles.contains("ROLE_SUPER_ADMIN");
  }
}
