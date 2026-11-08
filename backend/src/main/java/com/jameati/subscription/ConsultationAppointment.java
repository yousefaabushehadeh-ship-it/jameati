package com.jameati.subscription;

import com.jameati.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "consultation_appointments", indexes = {
    @Index(name = "idx_consultation_overlap", columnList = "status,starts_at,ends_at")})
@Getter
@Setter
public class ConsultationAppointment extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "membership_id", nullable = false)
  private AnnualMembership membership;
  @Column(nullable = false) private Instant startsAt;
  @Column(nullable = false) private Instant endsAt;
  @Column(nullable = false) private int durationMinutes;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ConsultationDtos.AppointmentStatus status = ConsultationDtos.AppointmentStatus.BOOKED;
  @Column(length = 2000) private String notes;
}
