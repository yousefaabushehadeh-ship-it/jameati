package com.jameati.catalog;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface CatalogRepository extends JpaRepository<CatalogItem,Long> {
  @org.springframework.transaction.annotation.Transactional
  @org.springframework.data.jpa.repository.Modifying
  @Query("update CatalogItem c set c.views=c.views+1 where c.id=:id")
  void incrementViews(@org.springframework.data.repository.query.Param("id") Long id);

  @Query("select c from CatalogItem c where c.type=:type and c.published=true and c.deleted=false "
      + "and (:q='' or lower(c.title) like lower(concat('%',:q,'%')) "
      + "or lower(c.summary) like lower(concat('%',:q,'%')) "
      + "or lower(coalesce(c.organization,'')) like lower(concat('%',:q,'%')) "
      + "or lower(coalesce(c.country,'')) like lower(concat('%',:q,'%')) "
      + "or lower(c.category) like lower(concat('%',:q,'%'))) "
      + "and (:category='' or c.category=:category)")
  Page<CatalogItem> publicSearch(
      CatalogType type,String q,String category,Pageable pageable);
  @Query("select c from CatalogItem c where c.type=:type and c.published=true and c.deleted=false "
      + "and (:q='' or lower(c.title) like lower(concat('%',:q,'%')) "
      + "or lower(c.summary) like lower(concat('%',:q,'%')) "
      + "or lower(coalesce(c.organization,'')) like lower(concat('%',:q,'%')) "
      + "or lower(coalesce(c.country,'')) like lower(concat('%',:q,'%')) "
      + "or lower(c.category) like lower(concat('%',:q,'%'))) "
      + "and (:category='' or c.category=:category)")
  java.util.List<CatalogItem> publicSearchAll(
      CatalogType type,String q,String category,Sort sort);
  Optional<CatalogItem> findByTypeAndSlugAndPublishedTrueAndDeletedFalse(CatalogType type,String slug);
  Optional<CatalogItem> findByIdAndDeletedFalse(Long id);
  Optional<CatalogItem> findByTypeAndSlugIgnoreCase(CatalogType type,String slug);
  @Query("select c from CatalogItem c where c.type=:type and c.deleted=false and (:q='' or lower(c.title) like lower(concat('%',:q,'%')) or lower(c.slug) like lower(concat('%',:q,'%')) or lower(c.category) like lower(concat('%',:q,'%')))")
  Page<CatalogItem> adminSearch(CatalogType type,String q,Pageable pageable);
  long countByTypeAndPublishedTrueAndDeletedFalse(CatalogType type);
  long countByTypeAndIdInAndPublishedTrueAndDeletedFalse(CatalogType type,Collection<Long> ids);
  java.util.List<CatalogItem> findAllByOrganizationAndDeletedFalse(String organization);
  java.util.List<CatalogItem> findByTypeAndIdInAndPublishedTrueAndDeletedFalse(
      CatalogType type,Collection<Long> ids);
  java.util.List<CatalogItem> findAllByTypeAndCountryAndDeletedFalse(
      CatalogType type,String country);
  Page<CatalogItem> findByTypeAndPublishedTrueAndDeletedFalse(
      CatalogType type,Pageable pageable);

  @Query("select c from CatalogItem c where c.type=com.jameati.catalog.CatalogType.JOB "
      + "and c.published=true and c.deleted=false")
  Page<CatalogItem> findJobRecommendationCandidates(Pageable pageable);
}
