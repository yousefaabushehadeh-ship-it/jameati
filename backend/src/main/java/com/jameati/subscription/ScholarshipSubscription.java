package com.jameati.subscription;

import com.jameati.common.BaseEntity;
import com.jameati.opportunity.Opportunity;
import com.jameati.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "scholarship_service_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class ScholarshipSubscription extends BaseEntity {
  @Version @Column(nullable = false) private long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "opportunity_id", nullable = false)
  private Opportunity opportunity;

  @Column(nullable = false, precision = 10, scale = 2) private BigDecimal amount;
  @Column(nullable = false, length = 10) private String currency;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PaymentStatus paymentStatus = PaymentStatus.PENDING;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ScholarshipApplicationStatus applicationStatus = ScholarshipApplicationStatus.AWAITING_PAYMENT;
  @Column(nullable = false, length = 40) private String paymentProvider = "MOCK";
  @Column(length = 100) private String paymentReference;
  private Instant paidAt;
  private Instant submittedAt;
  private Instant reviewedAt;
  private Instant contactedAt;

  @Column(length = 160) private String fullName;
  @Column(length = 40) private String phone;
  @Column(length = 40) private String whatsapp;
  @Column(length = 100) private String nationality;
  @Column(length = 100) private String residenceCountry;
  private LocalDate dateOfBirth;
  @Column(length = 100) private String degreeLevel;
  @Column(length = 180) private String university;
  @Column(length = 180) private String speciality;
  @Column(length = 20) private String graduationYear;
  @Column(length = 40) private String gpa;
  @Column(length = 80) private String englishLevel;
  @Column(columnDefinition = "text") private String notes;
  @Column(columnDefinition = "text") private String documentsText;
  @Column(columnDefinition = "text") private String reviewNotes;
  @Column(length = 190) private String reviewedBy;
}
