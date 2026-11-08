package com.jameati.crm;

import com.jameati.common.BaseEntity;
import com.jameati.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "crm_student_leads",
    indexes = {
      @Index(name = "idx_crm_lead_status_owner", columnList = "deleted,status,owner_id,updated_at"),
      @Index(name = "idx_crm_lead_follow_up", columnList = "deleted,next_follow_up_at"),
      @Index(name = "idx_crm_lead_email", columnList = "email"),
      @Index(name = "idx_crm_lead_phone", columnList = "phone")
    })
@Getter
@Setter
@NoArgsConstructor
public class StudentLead extends BaseEntity {
  @Version @Column(nullable = false) private long version;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", unique = true)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id")
  private User owner;

  @Column(name = "full_name", nullable = false, length = 160)
  private String fullName;

  @Column(nullable = false, length = 40)
  private String phone;

  @Column(nullable = false, length = 190)
  private String email;

  @Column(name = "date_of_birth") private LocalDate dateOfBirth;
  @Column(length = 100) private String city;
  @Column(name = "education_level", length = 100) private String educationLevel;
  @Column(length = 180) private String major;
  @Column(name = "english_level", length = 60) private String englishLevel;
  @Column(name = "main_need", length = 120) private String mainNeed;
  @Column(length = 180) private String interest;
  @Column(nullable = false, length = 80) private String source;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private LeadStatus status = LeadStatus.NEW;

  @Column(name = "last_contact_at") private Instant lastContactAt;
  @Column(name = "next_follow_up_at") private Instant nextFollowUpAt;
  @Column(name = "next_action", length = 500) private String nextAction;
  @Column(name = "service_package", length = 180) private String servicePackage;
  @Column(name = "amount_jod", precision = 12, scale = 2) private BigDecimal amountJod;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_status", nullable = false, length = 30)
  private CrmPaymentStatus paymentStatus = CrmPaymentStatus.NOT_APPLICABLE;

  @Column(name = "summary_notes", length = 2000) private String summaryNotes;
}
