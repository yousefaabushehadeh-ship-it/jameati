package com.jameati.me;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowedOrganizationRepository
    extends JpaRepository<FollowedOrganization, Long> {
  List<FollowedOrganization> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(Long userId);

  Optional<FollowedOrganization> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

  Optional<FollowedOrganization> findByUserIdAndOrganizationIgnoreCase(
      Long userId, String organization);

  Optional<FollowedOrganization> findByUserIdAndOrganizationItemId(
      Long userId, Long organizationId);
}
