package com.jameati.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.jameati.user.RoleName;
import com.jameati.user.UserRepository;
import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class RoleTestAccountsMigrationTest {
  private static final String PASSWORD = "Jameati@Test2026!";

  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired DataSource dataSource;

  @Test
  void createsAnEnabledVerifiedAccountForEveryRole() {
    new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V12__ensure_role_test_accounts.sql"))
        .execute(dataSource);

    var expectedRoles =
        Map.of(
            "test.user@jameati.test", Set.of(RoleName.USER),
            "test.data-entry@jameati.test", Set.of(RoleName.USER, RoleName.DATA_ENTRY),
            "test.editor@jameati.test", Set.of(RoleName.USER, RoleName.EDITOR),
            "test.admin@jameati.test", Set.of(RoleName.USER, RoleName.ADMIN),
            "test.super-admin@jameati.test",
                Set.of(RoleName.USER, RoleName.ADMIN, RoleName.SUPER_ADMIN));

    expectedRoles.forEach(
        (email, roles) -> {
          var user = users.findByEmailIgnoreCaseAndDeletedFalse(email).orElseThrow();
          assertThat(user.isEnabled()).isTrue();
          assertThat(user.getEmailVerifiedAt()).isNotNull();
          assertThat(user.getRoles()).containsExactlyInAnyOrderElementsOf(roles);
          assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
        });

    var jdbc = new JdbcTemplate(dataSource);
    assertThat(
            jdbc.queryForObject(
                """
                select count(*) from users
                where lower(email) in (
                  'test.user@jameati.test',
                  'test.data-entry@jameati.test',
                  'test.editor@jameati.test',
                  'test.admin@jameati.test',
                  'test.super-admin@jameati.test'
                )
                """,
                Integer.class))
        .isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                """
                select count(*) from user_roles assigned_role
                join users user_account on user_account.id = assigned_role.user_id
                where lower(user_account.email) in (
                  'test.user@jameati.test',
                  'test.data-entry@jameati.test',
                  'test.editor@jameati.test',
                  'test.admin@jameati.test',
                  'test.super-admin@jameati.test'
                )
                """,
                Integer.class))
        .isEqualTo(10);
  }
}
