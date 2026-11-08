package com.jameati.support;

import com.jameati.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "newsletter_subscribers",
    indexes = {@Index(name = "idx_newsletter_subscriber_active", columnList = "deleted,created_at")})
@Getter
@Setter
@NoArgsConstructor
public class NewsletterSubscriber extends BaseEntity {
  @Column(nullable = false, unique = true, length = 190)
  private String email;

  @Column(nullable = false, length = 30)
  private String frequency;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "newsletter_subscriber_topics",
      joinColumns = @JoinColumn(name = "subscriber_id"))
  @Column(name = "topic", nullable = false, length = 40)
  private Set<String> topics = new LinkedHashSet<>();

  @Column(name = "subscribed_at", nullable = false)
  private Instant subscribedAt;

  @Column(name = "unsubscribed_at")
  private Instant unsubscribedAt;
}
