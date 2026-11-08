package com.jameati.media;

import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
  Optional<MediaAsset> findByIdAndDeletedFalse(Long id);
  Optional<MediaAsset> findByStoredNameAndDeletedFalse(String storedName);

  @Query("select m from MediaAsset m where m.deleted=false and (:q='' or lower(m.originalName) like lower(concat('%',:q,'%')) or lower(m.uploadedBy) like lower(concat('%',:q,'%')))")
  Page<MediaAsset> search(String q, Pageable pageable);
}
