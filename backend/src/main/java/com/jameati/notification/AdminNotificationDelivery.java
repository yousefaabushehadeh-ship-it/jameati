package com.jameati.notification;

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
@Table(name = "admin_notification_deliveries")
@Getter
@Setter
@NoArgsConstructor
public class AdminNotificationDelivery extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by", nullable = false)
  private User createdBy;

  @Column(nullable = false, length = 180)
  private String title;

  @Column(nullable = false, length = 1000)
  private String body;

  @Column(length = 500)
  private String link;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationAudience audience;

  @Column(nullable = false)
  private boolean inAppEnabled;

  @Column(nullable = false)
  private boolean emailEnabled;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private NotificationDeliveryStatus status = NotificationDeliveryStatus.SCHEDULED;

  @Column(nullable = false)
  private Instant scheduledAt;

  private Instant claimedAt;
  private Instant startedAt;
  private Instant completedAt;

  @Column(nullable = false)
  private int recipientCount;

  @Column(nullable = false)
  private int inAppSentCount;

  @Column(nullable = false)
  private int emailSentCount;

  @Column(nullable = false)
  private int failedCount;

  @Column(length = 1000)
  private String errorMessage;
}
