package com.jameati.ai;

import com.jameati.common.BaseEntity;
import com.jameati.recommendation.RecommendationType;
import com.jameati.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "recommendation_explanation_cache",
    indexes = @Index(name = "idx_rec_explanation_lookup",
        columnList = "user_id,recommendation_type,candidate_id,input_fingerprint,deleted"),
    uniqueConstraints = @UniqueConstraint(name = "uk_rec_explanation_input",
        columnNames = {"user_id", "recommendation_type", "candidate_id", "input_fingerprint"}))
@Getter
@Setter
@NoArgsConstructor
public class RecommendationExplanationCache extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(name = "recommendation_type", nullable = false, length = 30)
  private RecommendationType recommendationType;

  @Column(name = "candidate_id", nullable = false)
  private Long candidateId;

  @Column(name = "input_fingerprint", nullable = false, length = 64)
  private String inputFingerprint;

  @Column(name = "payload_json", nullable = false, columnDefinition = "text")
  private String payloadJson;
}
