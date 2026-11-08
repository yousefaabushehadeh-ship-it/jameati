package com.jameati.payment.gateway.mpgs;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MpgsProperties.class)
@ConditionalOnProperty(prefix = "app.payment.mpgs", name = "enabled", havingValue = "true")
class MpgsConfiguration {
  @Bean
  RestClient mpgsRestClient(MpgsProperties properties, RestClient.Builder builder) {
    properties.validateEnabledConfiguration();
    var requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()));
    requestFactory.setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds()));
    return builder.requestFactory(requestFactory).defaultHeaders(headers -> {
      headers.setBasicAuth(properties.username(), properties.apiPassword());
      headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
      headers.setAccept(java.util.List.of(org.springframework.http.MediaType.APPLICATION_JSON));
    }).build();
  }
}
