package com.jameati.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ReleaseStartupLogger {
  static final String RELEASE_ID = "2026.09.20-ai-matching-v1";
  static final String READY_MARKER = "JAMEATI_RELEASE_READY";

  private static final Logger LOGGER = LoggerFactory.getLogger(ReleaseStartupLogger.class);

  @EventListener(ApplicationReadyEvent.class)
  void logReady() {
    LOGGER.info(
        "{} release={} registrationPhone=true membershipReceiptUpload=true "
            + "membershipReceiptAutoActivation=true arabicFirst=true xlsxData=true "
            + "opportunityAi=true recentViewsAtomic=true crm=true aiMatching=true databaseMigration=V21",
        READY_MARKER,
        RELEASE_ID);
  }
}
