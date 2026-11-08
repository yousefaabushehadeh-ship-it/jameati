package com.jameati.me;

import com.jameati.common.BaseEntity;
import com.jameati.opportunity.Opportunity;
import com.jameati.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "recently_viewed_opportunities",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "opportunity_id"}))
@Getter
@Setter
@NoArgsConstructor
public class RecentlyViewedOpportunity extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "opportunity_id", nullable = false)
  private Opportunity opportunity;

  private Instant viewedAt;
}
