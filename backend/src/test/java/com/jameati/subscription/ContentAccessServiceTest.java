package com.jameati.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jameati.catalog.CatalogType;
import com.jameati.user.RoleName;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContentAccessServiceTest {
  private UserRepository users;
  private AnnualMembershipRepository memberships;
  private SubscriptionPlanService plans;
  private ContentAccessService service;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    memberships = mock(AnnualMembershipRepository.class);
    plans = mock(SubscriptionPlanService.class);
    service = new ContentAccessService(users, memberships, plans);
  }

  @Test
  void anonymousVisitorOnlyGetsAdminSelectedFreeItems() {
    when(plans.current()).thenReturn(plan(true));
    var access = service.resolve((Principal) null);

    assertThat(service.isOpportunityLocked(access, 10L)).isFalse();
    assertThat(service.isOpportunityLocked(access, 99L)).isFalse(); // المنح صارت مجانية للكل
    assertThat(service.isCatalogLocked(access, CatalogType.COURSE, 20L)).isFalse();
    assertThat(service.isCatalogLocked(access, CatalogType.COURSE, 21L)).isTrue();
  }

  @Test
  void activePaidMemberGetsAllContent() {
    when(plans.current()).thenReturn(plan(true));
    var user = user(Set.of(RoleName.USER));
    var membership = new AnnualMembership();
    membership.setExpiresAt(Instant.now().plusSeconds(3600));
    when(users.findByEmailIgnoreCaseAndDeletedFalse("member@example.com")).thenReturn(Optional.of(user));
    when(memberships
        .findFirstByUserIdAndStatusAndStartsAtLessThanEqualAndExpiresAtAfterAndDeletedFalseOrderByExpiresAtDesc(
            eq(3L), eq(MembershipStatus.PAID), any(), any()))
        .thenReturn(Optional.of(membership));

    var access = service.resolve(() -> "member@example.com");

    assertThat(access.fullAccess()).isTrue();
    assertThat(access.expiresAt()).isEqualTo(membership.getExpiresAt());
    assertThat(service.isCatalogLocked(access, CatalogType.JOB, 999L)).isFalse();
  }

  @Test
  void staffGetsFullAccessWithoutMembership() {
    when(plans.current()).thenReturn(plan(true));
    when(users.findByEmailIgnoreCaseAndDeletedFalse("editor@example.com"))
        .thenReturn(Optional.of(user(Set.of(RoleName.USER, RoleName.EDITOR))));

    var access = service.resolve(() -> "editor@example.com");

    assertThat(access.fullAccess()).isTrue();
    verifyNoInteractions(memberships);
  }

  @Test
  void disablingSubscriptionRemovesThePaywall() {
    when(plans.current()).thenReturn(plan(false));
    var access = service.resolve((Principal) null);

    assertThat(service.isOpportunityLocked(access, 999L)).isFalse();
  }

  private static User user(Set<RoleName> roles) {
    var user = new User("member@example.com", "hash", "Member");
    user.setId(3L);
    user.setRoles(new java.util.HashSet<>(roles));
    return user;
  }

  private static SubscriptionPlanService.PlanConfig plan(boolean enabled) {
    return new SubscriptionPlanService.PlanConfig(
        enabled,
        new BigDecimal("120.00"),
        "USD",
        12,
        4,
        Map.of(
            SubscriptionPlanService.OPPORTUNITY, List.of(10L),
            CatalogType.COURSE.name(), List.of(20L)),
        "JAMEATI.CLIQ");
  }
}
