package com.jameati.crm;

import com.jameati.common.BaseEntity;
import com.jameati.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "crm_activities",
    indexes = {
      @Index(name = "idx_crm_activity_lead", columnList = "student_lead_id,occurred_at"),
      @Index(name = "idx_crm_activity_b2b", columnList = "b2b_account_id,occurred_at")
    })
@Getter
@Setter
@NoArgsConstructor
public class CrmActivity extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_lead_id")
  private StudentLead studentLead;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "b2b_account_id")
  private B2bAccount b2bAccount;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_id")
  private User actor;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private CrmActivityType type;

  @Column(nullable = false, length = 3000)
  private String details;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @PrePersist
  void validateTarget() {
    if ((studentLead == null) == (b2bAccount == null)) {
      throw new IllegalStateException("CRM activity must have exactly one target");
    }
    if (occurredAt == null) occurredAt = Instant.now();
  }
}
