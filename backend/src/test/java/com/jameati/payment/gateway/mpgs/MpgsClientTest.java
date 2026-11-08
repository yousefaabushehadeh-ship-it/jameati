package com.jameati.payment.gateway.mpgs;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MpgsClientTest {
  @Test void rejectedRequestPreservesOnlySafeHttpStatusAndCause() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var properties = new MpgsProperties(false, "https://example.test", "test", "test", "unused", "100", "https://example.test", 5, 20);
    var client = new MpgsClient(builder.build(), properties);
    server.expect(requestTo("https://example.test/rest/version/100/merchant/test/order/JAM-order/transaction/AUTH-tx"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
            .body("{\"error\":{\"cause\":\"INVALID_REQUEST\",\"explanation\":\"sensitive request detail\"}}"));
    assertThatThrownBy(() -> client.initiateAuthentication("JAM-order", "AUTH-tx", "SESSION-test", "35.00", "JOD"))
        .isInstanceOfSatisfying(MpgsGatewayException.class, error -> {
          assertThat(error.code()).isEqualTo("GATEWAY_HTTP_400_INVALID_REQUEST");
          assertThat(error.getMessage()).doesNotContain("sensitive", "SESSION");
        });
    server.verify();
  }
}
