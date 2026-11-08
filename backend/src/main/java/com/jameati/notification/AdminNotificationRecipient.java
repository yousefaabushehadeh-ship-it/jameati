package com.jameati.notification;

import com.jameati.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "admin_notification_recipients")
@Getter
@Setter
@NoArgsConstructor
public class AdminNotificationRecipient extends BaseEntity {
  @Column(nullable = false)
  private Long deliveryId;

  @Column(nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RecipientChannelStatus inAppStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RecipientChannelStatus emailStatus;

  @Column(length = 1000)
  private String inAppError;

  @Column(length = 1000)
  private String emailError;
}
