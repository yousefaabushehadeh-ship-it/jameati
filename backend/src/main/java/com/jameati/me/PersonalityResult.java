package com.jameati.me;

import com.jameati.common.BaseEntity;
import com.jameati.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "personality_results")
@Getter
@Setter
@NoArgsConstructor
public class PersonalityResult extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "personality_type", nullable = false, length = 20)
  private String personalityType;

  @Column(name = "scores_json", nullable = false, columnDefinition = "json")
  private String scoresJson;

  @Column(name = "answers_json", columnDefinition = "json")
  private String answersJson;

  @Column(nullable = false)
  private Instant completedAt;

  @Column(name = "latest_result", nullable = false)
  private boolean latestResult = true;
}
