package com.jameati.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ReleaseStartupLoggerTest {
  @Test
  void logsAnUnambiguousMarkerAfterTheApplicationIsReady(CapturedOutput output) {
    new ReleaseStartupLogger().logReady();

    assertThat(output)
        .contains("JAMEATI_RELEASE_READY")
        .contains("release=2026.09.20-ai-matching-v1")
        .contains("registrationPhone=true")
        .contains("membershipReceiptUpload=true")
        .contains("membershipReceiptAutoActivation=true")
        .contains("arabicFirst=true")
        .contains("xlsxData=true")
        .contains("opportunityAi=true")
        .contains("recentViewsAtomic=true")
        .contains("crm=true")
        .contains("aiMatching=true")
        .contains("databaseMigration=V21");
  }
}
