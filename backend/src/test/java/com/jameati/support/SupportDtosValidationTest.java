package com.jameati.support;

import static com.jameati.support.SupportDtos.*;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SupportDtosValidationTest {
  private final jakarta.validation.Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void rejectsInvalidContactPayload() {
    var request = new ContactRequest("", "not-an-email", "unknown", "ftp://page", "قصيرة", false);
    assertThat(validator.validate(request))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("name", "email", "subject", "pageUrl", "message", "consent");
  }

  @Test
  void acceptsSupportedNewsletterPreferences() {
    var request =
        new NewsletterRequest(
            "reader@example.com", "weekly", Set.of("opportunities", "guides"), true);
    assertThat(validator.validate(request)).isEmpty();
  }
}
