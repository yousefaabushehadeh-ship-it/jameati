package com.jameati.subscription;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A single calendar shared by all consultants; its database row also serializes reservations. */
@Entity
@Table(name = "consultation_schedule")
@Getter
@Setter
public class ConsultationSchedule {
  @Id private Long id = 1L;
  @Column(nullable = false) private boolean enabled = true;
  @Column(nullable = false) private boolean allDay = true;
  @Column(nullable = false, length = 60) private String timezone = "Asia/Amman";
  @Column(nullable = false, length = 20) private String workingDays = "1,2,3,4,5,6,7";
  @Column(nullable = false, length = 5) private String startTime = "00:00";
  @Column(nullable = false, length = 5) private String endTime = "23:59";
}
