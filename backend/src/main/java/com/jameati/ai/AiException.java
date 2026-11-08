package com.jameati.ai;

final class AiException extends RuntimeException {
  final int status;
  final long retryAfter;
  AiException(int status, String type) { this(status, type, 0); }
  AiException(int status, String type, long retryAfter) {
    super(type); this.status = status; this.retryAfter = retryAfter;
  }
}
