package com.jameati.crm;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface B2bAccountRepository
    extends JpaRepository<B2bAccount, Long>, JpaSpecificationExecutor<B2bAccount> {
  Optional<B2bAccount> findByIdAndDeletedFalse(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select account from B2bAccount account where account.id=:id and account.deleted=false")
  Optional<B2bAccount> findByIdForUpdate(@Param("id") Long id);
}
