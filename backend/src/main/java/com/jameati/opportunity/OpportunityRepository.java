package com.jameati.opportunity;

import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;

public interface OpportunityRepository extends JpaRepository<Opportunity,Long>,JpaSpecificationExecutor<Opportunity> {
  Optional<Opportunity> findBySlugAndDeletedFalseAndPublishedTrue(String slug);
  Optional<Opportunity> findByIdAndDeletedFalse(Long id);
  Optional<Opportunity> findBySlugIgnoreCase(String slug);
  Optional<Opportunity> findBySourceNameAndSourceExternalKey(String sourceName,String sourceExternalKey);
  List<Opportunity> findAllByOfficialUrlIgnoreCase(String officialUrl);
  boolean existsBySlugIgnoreCase(String slug);
  @Query("select o from Opportunity o where o.published=true and o.deleted=false and o.featured=true") List<Opportunity> featured(org.springframework.data.domain.Pageable pageable);
  @Modifying @Query("update Opportunity o set o.views=o.views+1 where o.id=:id") void incrementViews(@Param("id") Long id);
  List<Opportunity> findByIdInAndPublishedTrueAndDeletedFalse(Collection<Long> ids);
  Page<Opportunity> findByPublishedTrueAndDeletedFalse(Pageable pageable);
  long countByIdInAndPublishedTrueAndDeletedFalse(Collection<Long> ids);
  List<Opportunity> findAllBySourceNameAndDeletedFalse(String sourceName);

  @Query("select o from Opportunity o where o.published=true and o.deleted=false "
      + "and o.category in :categories "
      + "and (o.alwaysOpen=true or o.deadline is null or o.deadline>=:today)")
  Page<Opportunity> findRecommendationCandidates(
      @Param("categories") Collection<String> categories,
      @Param("today") LocalDate today,
      Pageable pageable);
}
