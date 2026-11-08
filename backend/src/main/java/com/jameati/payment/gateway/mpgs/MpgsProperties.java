package com.jameati.payment.gateway.mpgs;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment.mpgs")
public record MpgsProperties(
    boolean enabled,
    String baseUrl,
    String merchantId,
    String apiUsername,
    String apiPassword,
    String apiVersion,
    String frontendReturnUrl,
    int connectTimeoutSeconds,
    int readTimeoutSeconds) {
  public MpgsProperties {
    baseUrl = stripTrailingSlash(baseUrl);
    frontendReturnUrl = stripTrailingSlash(frontendReturnUrl);
    connectTimeoutSeconds = connectTimeoutSeconds <= 0 ? 5 : connectTimeoutSeconds;
    readTimeoutSeconds = readTimeoutSeconds <= 0 ? 20 : readTimeoutSeconds;
  }
  public void validateEnabledConfiguration() {
    if (!enabled) return;
    if (baseUrl == null || baseUrl.isBlank() || merchantId == null || merchantId.isBlank()
        || apiPassword == null || apiPassword.isBlank() || apiVersion == null
        || !apiVersion.matches("\\d+") || frontendReturnUrl == null || frontendReturnUrl.isBlank()) {
      throw new IllegalStateException("MPGS configuration is incomplete");
    }
    URI gateway = URI.create(baseUrl);
    URI callback = URI.create(frontendReturnUrl);
    if (!"https".equalsIgnoreCase(gateway.getScheme()) || gateway.getHost() == null
        || !(gateway.getHost().equalsIgnoreCase("gateway.mastercard.com")
            || gateway.getHost().toLowerCase(java.util.Locale.ROOT).endsWith(".gateway.mastercard.com"))) {
      throw new IllegalStateException("MPGS base URL must be an HTTPS Mastercard gateway URL");
    }
    boolean localCallback = callback.getHost() != null
        && (callback.getHost().equals("localhost") || callback.getHost().equals("127.0.0.1"));
    if (callback.getHost() == null || (!"https".equalsIgnoreCase(callback.getScheme()) && !localCallback)
        || callback.getRawQuery() != null || callback.getRawFragment() != null) {
      throw new IllegalStateException("MPGS frontend return URL must be an HTTPS origin without query or fragment");
    }
    if (connectTimeoutSeconds < 1 || connectTimeoutSeconds > 30
        || readTimeoutSeconds < 1 || readTimeoutSeconds > 120) {
      throw new IllegalStateException("MPGS timeout configuration is outside the allowed range");
    }
  }
  public String username() {
    return apiUsername == null || apiUsername.isBlank() ? "merchant." + merchantId : apiUsername.trim();
  }
  public URI endpoint(String path) {
    return URI.create(baseUrl + "/rest/version/" + apiVersion + "/merchant/" + merchantId + path);
  }
  public String hostedSessionScriptUrl() {
    var formBaseUrl = baseUrl.endsWith("/api") ? baseUrl.substring(0, baseUrl.length() - 4) : baseUrl;
    return formBaseUrl + "/form/version/" + apiVersion + "/merchant/" + merchantId + "/session.js";
  }
  private static String stripTrailingSlash(String value) {
    if (value == null) return null;
    var result = value.trim();
    while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
    return result;
  }
}
