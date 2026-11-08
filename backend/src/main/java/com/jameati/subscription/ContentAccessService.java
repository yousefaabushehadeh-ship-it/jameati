package com.jameati.subscription;

import com.jameati.catalog.CatalogType;
import com.jameati.user.RoleName;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.security.Principal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ContentAccessService {
  private static final EnumSet<RoleName> STAFF_ROLES = EnumSet.of(
      RoleName.DATA_ENTRY, RoleName.EDITOR, RoleName.ADMIN, RoleName.SUPER_ADMIN);

  private final UserRepository users;
  private final AnnualMembershipRepository memberships;
  private final SubscriptionPlanService plans;

  public ContentAccessService(
      UserRepository users,
      AnnualMembershipRepository memberships,
      SubscriptionPlanService plans) {
    this.users = users;
    this.memberships = memberships;
    this.plans = plans;
  }

  public AccessContext resolve(Principal principal) {
    var plan = plans.current();
    if (principal == null || principal.getName() == null) {
      return new AccessContext(false, false, null, plan);
    }
    return users.findByEmailIgnoreCaseAndDeletedFalse(principal.getName())
        .filter(User::isEnabled)
        .map(user -> resolve(user, plan))
        .orElseGet(() -> new AccessContext(false, false, null, plan));
  }

  public AccessContext resolve(User user) {
    return resolve(user, plans.current());
  }

  public boolean isOpportunityLocked(AccessContext access, Long opportunityId) {
    return isLocked(access, SubscriptionPlanService.OPPORTUNITY, opportunityId);
  }

  public boolean isCatalogLocked(AccessContext access, CatalogType type, Long itemId) {
    if (type == null) throw new IllegalArgumentException("نوع المحتوى مطلوب");
    return isLocked(access, type.name(), itemId);
  }

  private AccessContext resolve(User user, SubscriptionPlanService.PlanConfig plan) {
    if (user == null || user.getId() == null || !user.isEnabled()) {
      return new AccessContext(false, false, null, plan);
    }
    if (user.getRoles().stream().anyMatch(STAFF_ROLES::contains)) {
      return new AccessContext(true, true, null, plan);
    }
    var membership = activeMembership(user.getId(), Instant.now());
    return membership
        .map(item -> new AccessContext(true, true, item.getExpiresAt(), plan))
        .orElseGet(() -> new AccessContext(true, false, null, plan));
  }

  private boolean isLocked(AccessContext access, String contentType, Long contentId) {
    if (access == null || access.plan() == null || contentId == null) return true;
    if (!access.plan().enabled() || access.fullAccess()) return false;
    return !access.plan().freeContent().getOrDefault(contentType, java.util.List.of()).contains(contentId);
  }

  private Optional<AnnualMembership> activeMembership(Long userId, Instant now) {
    return memberships
        .findFirstByUserIdAndStatusAndStartsAtLessThanEqualAndExpiresAtAfterAndDeletedFalseOrderByExpiresAtDesc(
            userId, MembershipStatus.PAID, now, now);
  }

  public record AccessContext(
      boolean authenticated,
      boolean fullAccess,
      Instant expiresAt,
      SubscriptionPlanService.PlanConfig plan) {}
}
