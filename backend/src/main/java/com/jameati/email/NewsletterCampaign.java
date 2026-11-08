package com.jameati.email;

import com.jameati.common.BaseEntity;
import com.jameati.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "newsletter_campaigns")
@Getter
@Setter
@NoArgsConstructor
public class NewsletterCampaign extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by", nullable = false)
  private User createdBy;

  @Column(nullable = false, length = 200)
  private String subject;

  @Column(name = "preview_text", length = 300)
  private String previewText;

  @Column(name = "html_content", nullable = false, columnDefinition = "text")
  private String htmlContent;

  @Column(length = 40)
  private String topic;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private NewsletterCampaignStatus status = NewsletterCampaignStatus.DRAFT;

  private Instant scheduledAt;
  private Instant startedAt;
  private Instant completedAt;

  @Column(nullable = false)
  private int recipientCount;

  @Column(nullable = false)
  private int sentCount;

  @Column(nullable = false)
  private int failedCount;

  @Column(length = 1000)
  private String errorMessage;
}
