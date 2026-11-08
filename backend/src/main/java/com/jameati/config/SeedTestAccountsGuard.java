package com.jameati.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * المايجريشن V11/V12 بتزرع حسابات تجريبية (منها أدمن وسوبر أدمن) بباسوورد مكتوب بالكود.
 * على السيرفر الحقيقي هاي ثغرة، فبنعطّلها عند التشغيل إلا إذا TEST_ACCOUNTS_ENABLED=true.
 * بنعطّل بس الحسابات اللي لسا عليها الباسوورد الأصلي.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SeedTestAccountsGuard implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(SeedTestAccountsGuard.class);
  static final String SEED_HASH = "$2y$12$XB93.QlZT1lvUN6R8Zn86O8mtZrp4EhTwBos2kbUMQn.H6QpsvPmC";

  private final JdbcTemplate jdbc;
  private final boolean enabled;

  public SeedTestAccountsGuard(JdbcTemplate jdbc, @Value("${app.test-accounts.enabled:false}") boolean enabled) {
    this.jdbc = jdbc;
    this.enabled = enabled;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (enabled) return;
    int n = jdbc.update(
        "update users set enabled = false where lower(email) like '%@jameati.test' and password_hash = ? and enabled = true",
        SEED_HASH);
    if (n > 0) log.warn("Disabled {} seeded test accounts (set TEST_ACCOUNTS_ENABLED=true to keep them)", n);
  }
}
