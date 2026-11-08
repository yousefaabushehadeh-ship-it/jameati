package com.jameati.email;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<EmailToken> findByTokenHashAndTokenTypeAndUsedAtIsNullAndDeletedFalse(
      String tokenHash, EmailTokenType tokenType);

  List<EmailToken> findByUserIdAndTokenTypeAndUsedAtIsNullAndDeletedFalse(
      Long userId, EmailTokenType tokenType);
}
