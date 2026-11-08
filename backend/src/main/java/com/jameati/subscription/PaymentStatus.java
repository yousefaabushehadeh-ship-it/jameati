package com.jameati.subscription;

public enum PaymentStatus {
  PENDING,
  REQUIRES_AUTHENTICATION,
  AUTHENTICATED,
  PROCESSING,
  PAID,
  FAILED,
  CANCELLED,
  REFUNDED
}
