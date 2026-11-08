package com.jameati.me;

import com.jameati.common.BaseEntity;
import com.jameati.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_notifications")
@Getter
@Setter
@NoArgsConstructor
public class UserNotification extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 40)
  private String type;

  @Column(nullable = false, length = 180)
  private String title;

  @Column(nullable = false, length = 1000)
  private String body;

  @Column(length = 500)
  private String link;

  private Instant readAt;
}
