package com.jameati.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.jameati.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class BootstrapAdminTest {
  @Test
  void doesNotCreateAnyDefaultAdministrator() throws Exception {
    var users = mock(UserRepository.class);
    StartupDataConfiguration.bootstrapAdministrator(users, mock(PasswordEncoder.class), false, "", "", "Admin");
    verifyNoInteractions(users);
  }

  @Test
  void refusesBootstrapWithoutAStrongExplicitPassword() {
    assertThatThrownBy(() -> StartupDataConfiguration.bootstrapAdministrator(mock(UserRepository.class), mock(PasswordEncoder.class), true, "admin@example.com", "weak", "Admin"))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("12");
  }
}
