package com.jameati.user;

import static com.jameati.user.AdminUserDtos.*;

import com.jameati.common.NotFoundException;
import com.jameati.auth.RefreshTokenRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true)
public class AdminUserService {
  private final UserRepository repository;
  private final RefreshTokenRepository refreshTokens;

  public AdminUserService(UserRepository repository, RefreshTokenRepository refreshTokens) {
    this.repository = repository;
    this.refreshTokens = refreshTokens;
  }

  public Page<UserResponse> list(String query, Pageable pageable) {
    return repository.adminSearch(query == null ? "" : query.trim(), pageable).map(UserResponse::of);
  }

  @Transactional(isolation = Isolation.SERIALIZABLE)
  public UserResponse status(Long id, StatusRequest request, String actorEmail) {
    var user = user(id);
    if (!request.enabled() && user.getEmail().equalsIgnoreCase(actorEmail)) {
      throw new IllegalArgumentException("لا يمكنك تعطيل حسابك الحالي");
    }
    if (!request.enabled() && user.isEnabled() && user.getRoles().contains(RoleName.SUPER_ADMIN)
        && repository.countEnabledSuperAdmins() <= 1) {
      throw new IllegalArgumentException("لا يمكن تعطيل آخر مدير أعلى");
    }
    user.setEnabled(request.enabled());
    if (!request.enabled()) {
      var now = Instant.now();
      refreshTokens.findByUserIdAndRevokedAtIsNullAndDeletedFalse(user.getId())
          .forEach(token -> token.setRevokedAt(now));
    }
    return UserResponse.of(user);
  }

  @Transactional(isolation = Isolation.SERIALIZABLE)
  public UserResponse roles(Long id, RolesRequest request, String actorEmail) {
    var user = user(id);
    var roles = EnumSet.copyOf(request.roles());
    roles.add(RoleName.USER);
    var removesSuper = user.getRoles().contains(RoleName.SUPER_ADMIN)
        && !roles.contains(RoleName.SUPER_ADMIN);
    if (removesSuper && user.getEmail().equalsIgnoreCase(actorEmail)) {
      throw new IllegalArgumentException("لا يمكنك إزالة صلاحية المدير الأعلى عن حسابك الحالي");
    }
    if (removesSuper && user.isEnabled() && repository.countEnabledSuperAdmins() <= 1) {
      throw new IllegalArgumentException("لا يمكن إزالة صلاحية آخر مدير أعلى");
    }
    user.getRoles().clear();
    user.getRoles().addAll(roles);
    return UserResponse.of(user);
  }

  private User user(Long id) {
    return repository.findByIdAndDeletedFalse(id)
        .orElseThrow(() -> new NotFoundException("المستخدم غير موجود"));
  }
}
