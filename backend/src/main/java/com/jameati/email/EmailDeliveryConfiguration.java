package com.jameati.email;

import java.nio.charset.StandardCharsets;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

@Configuration(proxyBeanMethods = false)
public class EmailDeliveryConfiguration {
  @Bean
  EmailDeliveryGateway emailDeliveryGateway(
      ObjectProvider<JavaMailSender> mailSenderProvider,
      @Value("${app.mail.from:no-reply@jameati.local}") String from) {
    var mailSender = mailSenderProvider.getIfAvailable();
    if (mailSender != null) return smtpGateway(mailSender, from);

    var logger = LoggerFactory.getLogger("com.jameati.email.delivery");
    return message ->
        logger.info(
            "Development email transport (content redacted): recipient={}, subject={}",
            message.recipient(),
            message.subject());
  }

  private static EmailDeliveryGateway smtpGateway(JavaMailSender mailSender, String from) {
    return message -> mailSender.send(mimeMessage -> {
      var helper = new MimeMessageHelper(mimeMessage, StandardCharsets.UTF_8.name());
      helper.setFrom(from);
      helper.setTo(message.recipient());
      helper.setSubject(message.subject());
      helper.setText(message.content(), message.html());
    });
  }
}
