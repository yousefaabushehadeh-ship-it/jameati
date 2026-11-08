package com.jameati.payment.gateway.mpgs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MpgsPropertiesTest {
  @Test void derivesUsernameAndCanonicalEndpoints() {
    var properties = properties("https://test-network.mtf.gateway.mastercard.com/api/", "http://localhost:3000/");
    properties.validateEnabledConfiguration();
    assertThat(properties.username()).isEqualTo("merchant.TESTMERCHANT");
    assertThat(properties.endpoint("/session").toString()).isEqualTo(
        "https://test-network.mtf.gateway.mastercard.com/api/rest/version/100/merchant/TESTMERCHANT/session");
  }

  @Test void rejectsNonMastercardGatewayHost() {
    assertThatThrownBy(() -> properties("https://gateway.example/api", "https://jameati.example").validateEnabledConfiguration())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test void rejectsInsecureRemoteCallbackAndOpenRedirectParts() {
    assertThatThrownBy(() -> properties("https://test-network.mtf.gateway.mastercard.com/api", "http://jameati.example").validateEnabledConfiguration())
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> properties("https://test-network.mtf.gateway.mastercard.com/api", "https://jameati.example?next=https://evil.example").validateEnabledConfiguration())
        .isInstanceOf(IllegalStateException.class);
  }

  private static MpgsProperties properties(String baseUrl, String callback) {
    return new MpgsProperties(true, baseUrl, "TESTMERCHANT", "", "secret", "100", callback, 5, 20);
  }
}
