package com.jameati.support;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsletterSubscriberRepository
    extends JpaRepository<NewsletterSubscriber, Long> {
  Optional<NewsletterSubscriber> findByEmailIgnoreCase(String email);

  Optional<NewsletterSubscriber> findByEmailIgnoreCaseAndDeletedFalse(String email);

  Page<NewsletterSubscriber> findByDeletedFalse(Pageable pageable);

  List<NewsletterSubscriber> findByDeletedFalseOrderByIdAsc();
}
