package com.jameati.user;

import com.jameati.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity @Table(name="users") @Getter @Setter @NoArgsConstructor
public class User extends BaseEntity {
  @Column(nullable=false, unique=true, length=190) private String email;
  @Column(nullable=false) private String passwordHash;
  @Column(nullable=false, length=120) private String fullName;
  @Column(nullable=false) private boolean enabled = true;
  private Instant emailVerifiedAt;
  @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="user_roles", joinColumns=@JoinColumn(name="user_id"))
  @Enumerated(EnumType.STRING) @Column(name="role") private Set<RoleName> roles = new HashSet<>();
  public User(String email, String passwordHash, String fullName) { this.email=email; this.passwordHash=passwordHash; this.fullName=fullName; this.roles.add(RoleName.USER); }
}
