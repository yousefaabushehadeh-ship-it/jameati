package com.jameati.support;

import static com.jameati.support.SupportDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupportServiceTest {
  private ContactMessageRepository contacts;
  private NewsletterSubscriberRepository subscribers;
  private SupportService service;

  @BeforeEach
  void setUp() {
    contacts = mock(ContactMessageRepository.class);
    subscribers = mock(NewsletterSubscriberRepository.class);
    service = new SupportService(contacts, subscribers);
  }

  @Test
  void storesNormalizedContactAndReturnsReference() {
    when(contacts.existsByReferenceCode(any())).thenReturn(false);
    var receipt =
        service.submitContact(
            new ContactRequest(
                "  أنس أحمد  ",
                "  Anas@Example.COM ",
                "support",
                "",
                "هذه رسالة اختبار تحتوي على تفاصيل كافية للإرسال",
                true));

    var captor = org.mockito.ArgumentCaptor.forClass(ContactMessage.class);
    verify(contacts).save(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("أنس أحمد");
    assertThat(captor.getValue().getEmail()).isEqualTo("anas@example.com");
    assertThat(captor.getValue().getPageUrl()).isNull();
    assertThat(receipt.referenceCode()).startsWith("JM-");
    assertThat(receipt.status()).isEqualTo("NEW");
  }

  @Test
  void upsertReactivatesExistingSubscriptionAndReplacesPreferences() {
    var existing = new NewsletterSubscriber();
    existing.setId(41L);
    existing.setEmail("anas@example.com");
    existing.setFrequency("weekly");
    existing.setTopics(new LinkedHashSet<>(Set.of("guides")));
    existing.setDeleted(true);
    existing.setUnsubscribedAt(Instant.now().minusSeconds(60));
    when(subscribers.findByEmailIgnoreCase("anas@example.com")).thenReturn(Optional.of(existing));

    var result =
        service.subscribe(
            new NewsletterRequest(
                " Anas@Example.com ",
                "deadlines",
                Set.of("opportunities", "jobs"),
                true));

    assertThat(result.active()).isTrue();
    assertThat(result.email()).isEqualTo("anas@example.com");
    assertThat(existing.isDeleted()).isFalse();
    assertThat(existing.getUnsubscribedAt()).isNull();
    assertThat(existing.getTopics()).containsExactlyInAnyOrder("opportunities", "jobs");
    verify(subscribers).save(existing);
  }

  @Test
  void unsubscribeIsIdempotentAndSoftDeletesAnActiveSubscription() {
    var existing = new NewsletterSubscriber();
    existing.setEmail("anas@example.com");
    existing.setDeleted(false);
    when(subscribers.findByEmailIgnoreCaseAndDeletedFalse("anas@example.com"))
        .thenReturn(Optional.of(existing));

    var result = service.unsubscribe(" ANAS@example.com ");

    assertThat(result.active()).isFalse();
    assertThat(existing.isDeleted()).isTrue();
    assertThat(existing.getUnsubscribedAt()).isNotNull();
    verify(subscribers).save(existing);
  }
}
