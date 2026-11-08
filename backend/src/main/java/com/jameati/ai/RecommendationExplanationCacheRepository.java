package com.jameati.ai;

import com.jameati.recommendation.RecommendationType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationExplanationCacheRepository
    extends JpaRepository<RecommendationExplanationCache, Long> {
  Optional<RecommendationExplanationCache>
      findByUserIdAndRecommendationTypeAndCandidateIdAndInputFingerprintAndDeletedFalse(
          Long userId, RecommendationType recommendationType, Long candidateId,
          String inputFingerprint);

  void deleteByUserIdAndRecommendationTypeAndCandidateId(
      Long userId, RecommendationType recommendationType, Long candidateId);
}
