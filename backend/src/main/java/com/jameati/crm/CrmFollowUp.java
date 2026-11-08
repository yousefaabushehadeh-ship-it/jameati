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
    name = "crm_follow_ups",
    indexes = {
      @Index(name = "idx_crm_follow_up_queue", columnList = "deleted,status,due_at,owner_id"),
      @Index(name = "idx_crm_follow_up_lead", columnList = "student_lead_id,status,due_at"),
      @Index(name = "idx_crm_follow_up_b2b", columnList = "b2b_account_id,status,due_at")
    })
@Getter
@Setter
@NoArgsConstructor
public class CrmFollowUp extends BaseEntity {
  @Version @Column(nullable = false) private long version;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_lead_id")
  private StudentLead studentLead;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "b2b_account_id")
  private B2bAccount b2bAccount;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id")
  private User owner;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "completed_by")
  private User completedBy;

  @Column(name = "due_at", nullable = false) private Instant dueAt;
  @Column(nullable = false, length = 500) private String action;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private FollowUpStatus status = FollowUpStatus.PENDING;

  @Column(length = 2000) private String result;
  @Column(name = "completed_at") private Instant completedAt;

  @PrePersist
  @PreUpdate
  void validateTarget() {
    if ((studentLead == null) == (b2bAccount == null)) {
      throw new IllegalStateException("CRM follow-up must have exactly one target");
    }
  }
}
