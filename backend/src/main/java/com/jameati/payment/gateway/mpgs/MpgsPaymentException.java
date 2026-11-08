package com.jameati.payment.gateway.mpgs;

public class MpgsPaymentException extends RuntimeException {
  private final String code;

  public MpgsPaymentException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() { return code; }
}
