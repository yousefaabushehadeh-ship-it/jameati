package com.jameati.support;

import static com.jameati.support.SupportDtos.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportService {
  private final ContactMessageRepository contactMessages;
  private final NewsletterSubscriberRepository newsletterSubscribers;

  public SupportService(
      ContactMessageRepository contactMessages,
      NewsletterSubscriberRepository newsletterSubscribers) {
    this.contactMessages = contactMessages;
    this.newsletterSubscribers = newsletterSubscribers;
  }

  @Transactional
  public ContactReceipt submitContact(ContactRequest request) {
    var submittedAt = Instant.now();
    var contact = new ContactMessage();
    contact.setReferenceCode(nextReferenceCode());
    contact.setName(request.name().trim());
    contact.setEmail(normalizeEmail(request.email()));
    contact.setSubject(request.subject());
    contact.setPageUrl(blankToNull(request.pageUrl()));
    contact.setMessage(request.message().trim());
    contact.setConsent(Boolean.TRUE.equals(request.consent()));
    contact.setStatus("NEW");
    contactMessages.save(contact);
    return new ContactReceipt(contact.getReferenceCode(), contact.getStatus(), submittedAt);
  }

  @Transactional
  public NewsletterResponse subscribe(NewsletterRequest request) {
    var email = normalizeEmail(request.email());
    var now = Instant.now();
    var subscriber =
        newsletterSubscribers.findByEmailIgnoreCase(email).orElseGet(NewsletterSubscriber::new);

    if (subscriber.getId() == null || subscriber.isDeleted()) {
      subscriber.setSubscribedAt(now);
    }
    subscriber.setEmail(email);
    subscriber.setFrequency(request.frequency());
    subscriber.setTopics(new LinkedHashSet<>(request.topics()));
    subscriber.setUnsubscribedAt(null);
    subscriber.setDeleted(false);
    newsletterSubscribers.save(subscriber);
    return toNewsletterResponse(subscriber, now);
  }

  @Transactional
  public UnsubscribeResponse unsubscribe(String rawEmail) {
    var email = normalizeEmail(rawEmail);
    newsletterSubscribers
        .findByEmailIgnoreCaseAndDeletedFalse(email)
        .ifPresent(
            subscriber -> {
              subscriber.setDeleted(true);
              subscriber.setUnsubscribedAt(Instant.now());
              newsletterSubscribers.save(subscriber);
            });
    return new UnsubscribeResponse(email, false);
  }

  @Transactional(readOnly = true)
  public Page<ContactAdminResponse> contacts(Pageable pageable) {
    return contactMessages.findByDeletedFalse(pageable).map(this::toContactAdminResponse);
  }

  @Transactional(readOnly = true)
  public Page<NewsletterAdminResponse> subscribers(Pageable pageable) {
    return newsletterSubscribers
        .findByDeletedFalse(pageable)
        .map(this::toNewsletterAdminResponse);
  }

  private String nextReferenceCode() {
    String reference;
    do {
      reference =
          "JM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    } while (contactMessages.existsByReferenceCode(reference));
    return reference;
  }

  private NewsletterResponse toNewsletterResponse(
      NewsletterSubscriber subscriber, Instant fallbackUpdatedAt) {
    return new NewsletterResponse(
        subscriber.getEmail(),
        subscriber.getFrequency(),
        Set.copyOf(subscriber.getTopics()),
        !subscriber.isDeleted(),
        subscriber.getSubscribedAt(),
        subscriber.getUpdatedAt() == null ? fallbackUpdatedAt : subscriber.getUpdatedAt());
  }

  private ContactAdminResponse toContactAdminResponse(ContactMessage contact) {
    return new ContactAdminResponse(
        contact.getId(),
        contact.getReferenceCode(),
        contact.getName(),
        contact.getEmail(),
        contact.getSubject(),
        contact.getPageUrl(),
        contact.getMessage(),
        contact.getStatus(),
        contact.getCreatedAt());
  }

  private NewsletterAdminResponse toNewsletterAdminResponse(NewsletterSubscriber subscriber) {
    return new NewsletterAdminResponse(
        subscriber.getId(),
        subscriber.getEmail(),
        subscriber.getFrequency(),
        Set.copyOf(subscriber.getTopics()),
        subscriber.getSubscribedAt(),
        subscriber.getUpdatedAt());
  }

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
