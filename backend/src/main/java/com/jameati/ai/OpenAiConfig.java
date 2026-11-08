package com.jameati.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Component @ConfigurationProperties(prefix="app.ai") @Validated @Getter @Setter
public class OpenAiConfig {
  private String apiKey = "";
  private String model = "gpt-4.1-mini";
  @Min(100) @Max(4000) private int maxOutputTokens = 900;
  @Min(1) @Max(1000) private int messagesPerHour = 20;
  @Min(1) @Max(100000) private int globalMessagesPerHour = 1000;
  @Min(1) @Max(60) private int timeoutSeconds = 30;
}
