package com.jameati.email;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class NewsletterCampaignDtos {
  private NewsletterCampaignDtos() {}

  public record CampaignRequest(
      @NotBlank @Size(max = 200) String subject,
      @Size(max = 300) String previewText,
      @NotBlank @Size(max = 200000) String htmlContent,
      @Pattern(regexp = "opportunities|courses|jobs|guides") String topic,
      Instant scheduledAt,
      boolean sendNow) {}

  public record CampaignResponse(
      Long id,
      String subject,
      String previewText,
      String topic,
      NewsletterCampaignStatus status,
      Instant scheduledAt,
      Instant startedAt,
      Instant completedAt,
      int recipientCount,
      int sentCount,
      int failedCount,
      String errorMessage,
      Long createdBy,
      Instant createdAt,
      Instant updatedAt) {}
}
