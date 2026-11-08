package com.jameati.me;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CvDocumentRepository extends JpaRepository<CvDocument, Long> {
  List<CvDocument> findByUserIdAndDeletedFalseOrderByUpdatedAtDesc(Long userId);

  Optional<CvDocument> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

  List<CvDocument> findByUserIdAndDeletedFalseAndPrimaryDocumentTrue(Long userId);
}
