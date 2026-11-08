package com.jameati.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class AuthDtosValidationTest {
  private final jakarta.validation.Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void registrationRequiresAValidPhone() {
    var missing =
        new AuthDtos.RegisterRequest(
            "طالب", "student@example.com", null, "national-number");
    var malformed =
        new AuthDtos.RegisterRequest(
            "طالب", "student@example.com", "phone-number", "national-number");

    assertThat(validator.validate(missing))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("phone");
    assertThat(validator.validate(malformed))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("phone");
  }

  @Test
  void registrationAcceptsLocalAndInternationalPhoneFormats() {
    var local =
        new AuthDtos.RegisterRequest(
            "طالب", "student@example.com", "079-123-4567", "national-number");
    var international =
        new AuthDtos.RegisterRequest(
            "طالب", "student@example.com", "+٩٦٢ (٧٩) ١٢٣ ٤٥٦٧", "national-number");

    assertThat(validator.validate(local)).isEmpty();
    assertThat(validator.validate(international)).isEmpty();
  }
}
