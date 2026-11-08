package com.jameati.me;

import com.jameati.common.BaseEntity;
import com.jameati.catalog.CatalogItem;
import com.jameati.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "followed_organizations",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "organization"}))
@Getter
@Setter
@NoArgsConstructor
public class FollowedOrganization extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id")
  private CatalogItem organizationItem;

  @Column(nullable = false, length = 180)
  private String organization;

  @Column(length = 100)
  private String country;

  @Column(length = 500)
  private String website;
}
