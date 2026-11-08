package com.jameati.payment.gateway.mpgs;

public class MpgsGatewayException extends RuntimeException {
  private final String code;
  public MpgsGatewayException(String message) { super(message); this.code = "GATEWAY_UNAVAILABLE"; }
  public MpgsGatewayException(String message, Throwable cause) { super(message, cause); this.code = "GATEWAY_UNAVAILABLE"; }
  public MpgsGatewayException(int httpStatus, String cause) {
    super("MPGS rejected the request with HTTP " + httpStatus);
    this.code = "GATEWAY_HTTP_" + httpStatus + (cause == null ? "" : "_" + cause);
  }
  public String code() { return code; }
}
