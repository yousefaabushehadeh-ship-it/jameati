package com.jameati.importing;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class OfficialOpportunityImportConfiguration {
  @Bean
  @Order(100)
  @ConditionalOnProperty(
      name = "app.official-import.auto-run",
      havingValue = "true",
      matchIfMissing = false)
  CommandLineRunner importOfficialOpportunities(OfficialOpportunityImportService service) {
    return args -> {
      var preview = service.preview();
      if (preview.createCount() > 0 || preview.updateCount() > 0) {
        service.run();
      }
    };
  }
}
