package com.jameati.subscription;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnualMembershipRepository extends JpaRepository<AnnualMembership, Long> {
  @Query("select m from AnnualMembership m where m.user.id = :userId and m.status = :status "
      + "and m.startsAt <= :startsAt and m.expiresAt > :expiresAt and m.deleted = false "
      + "and m.planCode <> com.jameati.subscription.MembershipPlanCode.CONSULTATION order by m.expiresAt desc limit 1")
  Optional<AnnualMembership> findFirstByUserIdAndStatusAndStartsAtLessThanEqualAndExpiresAtAfterAndDeletedFalseOrderByExpiresAtDesc(
      @Param("userId") Long userId, @Param("status") MembershipStatus status,
      @Param("startsAt") Instant startsAt, @Param("expiresAt") Instant expiresAt);

  Optional<AnnualMembership> findFirstByUserIdAndPlanCodeAndStatusAndPaymentProviderAndDeletedFalseOrderByCreatedAtDesc(
      Long userId, MembershipPlanCode planCode, MembershipStatus status, String paymentProvider);

  java.util.List<AnnualMembership> findByUserIdAndPlanCodeAndStatusAndDeletedFalseOrderByCreatedAtDesc(
      Long userId, MembershipPlanCode planCode, MembershipStatus status);

  java.util.List<AnnualMembership> findByPlanCodeAndStatusAndDeletedFalseOrderByCreatedAtDesc(
      MembershipPlanCode planCode, MembershipStatus status);

  Optional<AnnualMembership> findFirstByUserIdAndStatusAndPaymentProviderAndDeletedFalseOrderByCreatedAtDesc(
      Long userId, MembershipStatus status, String paymentProvider);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select membership from AnnualMembership membership "
      + "where membership.id = :id and membership.deleted = false")
  Optional<AnnualMembership> findByIdForUpdate(@Param("id") Long id);

  Optional<AnnualMembership> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select membership from AnnualMembership membership where membership.id = :id "
      + "and membership.user.id = :userId and membership.deleted = false")
  Optional<AnnualMembership> findByIdAndUserIdForUpdate(
      @Param("id") Long id, @Param("userId") Long userId);

  @Query("select (count(m) > 0) from AnnualMembership m where m.paymentProvider = 'MPGS' "
      + "and m.paymentStatus = com.jameati.subscription.PaymentStatus.PAID and m.deleted = false "
      + "and m.id <> :membershipId and (m.gatewayOrderId = :orderId or m.gatewayTransactionId = :transactionId)")
  boolean existsPaidMpgsTransactionForAnotherMembership(
      @Param("orderId") String orderId, @Param("transactionId") String transactionId,
      @Param("membershipId") Long membershipId);

  Optional<AnnualMembership> findFirstByReceiptSha256(String receiptSha256);

  Page<AnnualMembership> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);

  Page<AnnualMembership> findByDeletedFalse(Pageable pageable);

  Page<AnnualMembership> findByStatusAndDeletedFalse(MembershipStatus status, Pageable pageable);
}
