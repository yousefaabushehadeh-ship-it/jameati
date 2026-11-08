package com.jameati.auth;

import com.jameati.common.BaseEntity;
import com.jameati.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity @Table(name="refresh_tokens") @Getter @Setter @NoArgsConstructor
public class RefreshToken extends BaseEntity {
  @Column(nullable=false, unique=true, length=64) private String tokenHash;
  @ManyToOne(fetch=FetchType.LAZY, optional=false) private User user;
  @Column(nullable=false) private Instant expiresAt;
  private Instant revokedAt;
}
