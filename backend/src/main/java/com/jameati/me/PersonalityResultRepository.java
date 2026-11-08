package com.jameati.me;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalityResultRepository extends JpaRepository<PersonalityResult, Long> {
  List<PersonalityResult> findByUserIdAndDeletedFalseOrderByCompletedAtDesc(Long userId);

  Optional<PersonalityResult> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

  List<PersonalityResult> findByUserIdAndDeletedFalseAndLatestResultTrue(Long userId);
}
