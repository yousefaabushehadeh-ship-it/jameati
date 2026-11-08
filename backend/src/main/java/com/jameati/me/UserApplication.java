package com.jameati.me;

import com.jameati.common.BaseEntity;
import com.jameati.opportunity.Opportunity;
import com.jameati.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "user_applications",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "opportunity_id"}))
@Getter
@Setter
@NoArgsConstructor
public class UserApplication extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "opportunity_id", nullable = false)
  private Opportunity opportunity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ApplicationStatus status = ApplicationStatus.PLANNING;

  @Column(columnDefinition = "text")
  private String notes;

  private Instant appliedAt;

  private Instant nextActionAt;
}
