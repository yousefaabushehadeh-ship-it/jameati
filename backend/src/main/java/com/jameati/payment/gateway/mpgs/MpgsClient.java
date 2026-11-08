package com.jameati.payment.gateway.mpgs;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(prefix = "app.payment.mpgs", name = "enabled", havingValue = "true")
public class MpgsClient {
  private final RestClient client;
  private final MpgsProperties properties;
  public MpgsClient(RestClient mpgsRestClient, MpgsProperties properties) {
    this.client = mpgsRestClient;
    this.properties = properties;
  }
  public JsonNode createSession() {
    return post(properties.endpoint("/session"), Map.of("apiOperation", "CREATE_SESSION"));
  }
  public JsonNode initiateAuthentication(String orderId, String transactionId, String sessionId, String amount, String currency) {
    return put(transactionUri(orderId, transactionId), Map.of(
        "apiOperation", "INITIATE_AUTHENTICATION",
        "authentication", Map.of("channel", "PAYER_BROWSER", "purpose", "PAYMENT_TRANSACTION", "acceptVersions", "3DS2"),
        "order", Map.of("amount", amount, "currency", currency), "session", Map.of("id", sessionId)));
  }
  public JsonNode authenticatePayer(String orderId, String transactionId, String sessionId, String amount,
      String currency, String redirectResponseUrl, Map<String, Object> device) {
    return put(transactionUri(orderId, transactionId), Map.of(
        "apiOperation", "AUTHENTICATE_PAYER",
        "authentication", Map.of("channel", "PAYER_BROWSER", "purpose", "PAYMENT_TRANSACTION",
            "redirectResponseUrl", redirectResponseUrl),
        "device", device, "order", Map.of("amount", amount, "currency", currency),
        "session", Map.of("id", sessionId)));
  }
  public JsonNode retrieveTransaction(String orderId, String transactionId) {
    return get(transactionUri(orderId, transactionId));
  }
  public JsonNode pay(String orderId, String paymentTransactionId, String authenticationTransactionId,
      String sessionId, String amount, String currency) {
    return put(transactionUri(orderId, paymentTransactionId), Map.of(
        "apiOperation", "PAY", "authentication", Map.of("transactionId", authenticationTransactionId),
        "order", Map.of("amount", amount, "currency", currency), "session", Map.of("id", sessionId)));
  }
  public String hostedSessionScriptUrl() { return properties.hostedSessionScriptUrl(); }
  public String returnUrl(long paymentId) {
    return properties.frontendReturnUrl() + "/subscription?mpgsPaymentId=" + paymentId + "&mpgsCallback=1";
  }
  private URI transactionUri(String orderId, String transactionId) {
    return properties.endpoint("/order/" + encode(orderId) + "/transaction/" + encode(transactionId));
  }
  private JsonNode post(URI uri, Object body) { return exchange("POST", uri, body); }
  private JsonNode put(URI uri, Object body) { return exchange("PUT", uri, body); }
  private JsonNode get(URI uri) {
    try {
      return client.get().uri(uri).retrieve().onStatus(HttpStatusCode::isError, (request, response) -> {
        throw gatewayError(response);
      }).body(JsonNode.class);
    } catch (MpgsGatewayException exception) { throw exception; }
    catch (RestClientException exception) { throw new MpgsGatewayException("MPGS is temporarily unavailable", exception); }
  }
  private JsonNode exchange(String method, URI uri, Object body) {
    try {
      var request = "POST".equals(method) ? client.post() : client.put();
      return request.uri(uri).body(body).retrieve().onStatus(HttpStatusCode::isError, (sent, response) -> {
        throw gatewayError(response);
      }).body(JsonNode.class);
    } catch (MpgsGatewayException exception) { throw exception; }
    catch (RestClientException exception) { throw new MpgsGatewayException("MPGS is temporarily unavailable", exception); }
  }
  private static MpgsGatewayException gatewayError(org.springframework.http.client.ClientHttpResponse response)
      throws java.io.IOException {
    // Only allow documented error categories out; descriptions may echo request secrets.
    String cause = null;
    try {
      var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getBody().readNBytes(16384));
      var candidate = body == null ? "" : body.path("error").path("cause").asText();
      if (java.util.Set.of("INVALID_REQUEST", "SERVER_BUSY", "SERVER_FAILED", "REQUEST_REJECTED").contains(candidate)) cause = candidate;
    } catch (java.io.IOException ignored) { /* HTTP status remains useful without a JSON body. */ }
    return new MpgsGatewayException(response.getStatusCode().value(), cause);
  }
  private static String encode(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }
}
