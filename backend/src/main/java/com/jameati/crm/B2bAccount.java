package com.jameati.crm;

import com.jameati.common.BaseEntity;
import com.jameati.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "crm_b2b_accounts",
    indexes = {
      @Index(name = "idx_crm_b2b_stage_owner", columnList = "deleted,stage,owner_id,updated_at"),
      @Index(name = "idx_crm_b2b_follow_up", columnList = "deleted,next_follow_up_at")
    })
@Getter
@Setter
@NoArgsConstructor
public class B2bAccount extends BaseEntity {
  @Version @Column(nullable = false) private long version;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id")
  private User owner;

  @Column(nullable = false, length = 200) private String organization;

  @Enumerated(EnumType.STRING)
  @Column(name = "organization_type", nullable = false, length = 30)
  private OrganizationType type = OrganizationType.ORGANIZATION;

  @Column(name = "contact_person", nullable = false, length = 160) private String contactPerson;
  @Column(name = "job_title", length = 160) private String jobTitle;
  @Column(nullable = false, length = 40) private String phone;
  @Column(length = 190) private String email;
  @Column(length = 100) private String city;
  @Column(name = "student_count") private Integer studentCount;
  @Column(nullable = false, length = 100) private String source;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private B2bStage stage = B2bStage.PROSPECT;

  @Column(name = "last_contact_at") private Instant lastContactAt;
  @Column(name = "next_follow_up_at") private Instant nextFollowUpAt;
  @Column(name = "next_action", length = 500) private String nextAction;
  @Column(length = 500) private String offer;
  @Column(name = "estimated_value_jod", precision = 12, scale = 2) private BigDecimal estimatedValueJod;
  @Column(name = "proposal_sent_at") private Instant proposalSentAt;
  @Column(nullable = false) private int probability;
  @Column(name = "summary_notes", length = 2000) private String summaryNotes;
}
