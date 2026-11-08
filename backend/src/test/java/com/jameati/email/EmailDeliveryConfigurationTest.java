package com.jameati.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

class EmailDeliveryConfigurationTest {
  @Test
  void usesJavaMailSenderWhenItIsAvailable() throws Exception {
    var sender = mock(JavaMailSender.class);
    var beanFactory = new StaticListableBeanFactory();
    beanFactory.addBean("mailSender", sender);
    var gateway = new EmailDeliveryConfiguration()
        .emailDeliveryGateway(beanFactory.getBeanProvider(JavaMailSender.class), "team@jameati.test");
    var message = new MimeMessage(Session.getInstance(new Properties()));

    org.mockito.Mockito.doAnswer(invocation -> {
      MimeMessagePreparator preparator = invocation.getArgument(0);
      preparator.prepare(message);
      return null;
    }).when(sender).send(org.mockito.ArgumentMatchers.any(MimeMessagePreparator.class));

    gateway.send(new EmailDeliveryGateway.EmailMessage(
        "student@example.com", "مرحبًا", "<strong>أهلًا</strong>", true));

    verify(sender).send(org.mockito.ArgumentMatchers.any(MimeMessagePreparator.class));
    message.saveChanges();
    assertThat(message.getFrom()[0].toString()).isEqualTo("team@jameati.test");
    assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString())
        .isEqualTo("student@example.com");
    assertThat(message.getSubject()).isEqualTo("مرحبًا");
    assertThat(message.getContentType()).startsWith("text/html");
    assertThat(message.getContent().toString()).contains("أهلًا");
  }

  @Test
  void fallsBackOnlyWhenNoMailSenderExists() {
    var beanFactory = new StaticListableBeanFactory();
    var gateway = new EmailDeliveryConfiguration()
        .emailDeliveryGateway(beanFactory.getBeanProvider(JavaMailSender.class), "team@jameati.test");

    gateway.send(new EmailDeliveryGateway.EmailMessage(
        "student@example.com", "development", "secret-token", false));

    assertThat(gateway).isNotNull();
  }
}
