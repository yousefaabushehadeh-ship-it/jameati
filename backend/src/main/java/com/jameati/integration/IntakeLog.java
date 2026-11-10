package com.jameati.integration;

import com.jameati.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** سطر لكل منشور وصل من Tab، حتى المكرر والمرفوض. */
@Entity @Table(name = "intake_log")
@Getter @Setter @NoArgsConstructor
public class IntakeLog extends BaseEntity {
  @Column(length = 300) private String title;
  @Enumerated(EnumType.STRING) @Column(length = 20) private OpportunityIntakeService.Kind kind;
  @Column(nullable = false, length = 20) private String status;
  @Column(length = 255) private String reason;
  private Long itemId;
  @Column(length = 190) private String itemSlug;
}
