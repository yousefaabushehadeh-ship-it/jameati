package com.jameati.email;

public interface EmailDeliveryGateway {
  void send(EmailMessage message);

  record EmailMessage(String recipient, String subject, String content, boolean html) {}
}
