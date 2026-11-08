package com.jameati.crm;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentLeadRepository
    extends JpaRepository<StudentLead, Long>, JpaSpecificationExecutor<StudentLead> {
  Optional<StudentLead> findByIdAndDeletedFalse(Long id);
  Optional<StudentLead> findByUserIdAndDeletedFalse(Long userId);
  Optional<StudentLead> findFirstByEmailIgnoreCaseAndDeletedFalse(String email);
  Optional<StudentLead> findFirstByEmailIgnoreCaseAndUserIsNullAndDeletedFalse(String email);
  List<StudentLead> findByPhoneAndDeletedFalse(String phone);
  List<StudentLead> findByPhoneAndUserIsNullAndDeletedFalse(String phone);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select lead from StudentLead lead where lead.id=:id and lead.deleted=false")
  Optional<StudentLead> findByIdForUpdate(@Param("id") Long id);

  @Query("select coalesce(sum(lead.amountJod),0) from StudentLead lead where lead.deleted=false and lead.paymentStatus=com.jameati.crm.CrmPaymentStatus.PAID")
  BigDecimal paidRevenueJod();
}
