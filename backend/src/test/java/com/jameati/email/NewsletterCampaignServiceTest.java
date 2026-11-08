package com.jameati.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jameati.support.NewsletterSubscriber;
import com.jameati.support.NewsletterSubscriberRepository;
import com.jameati.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NewsletterCampaignServiceTest {
  @Test
  void sendsAnImmediateCampaignOnlyToMatchingActiveSubscribers() {
    var campaigns = mock(NewsletterCampaignRepository.class);
    var subscribers = mock(NewsletterSubscriberRepository.class);
    var delivery = mock(EmailDeliveryGateway.class);
    var service = new NewsletterCampaignService(campaigns, subscribers, delivery);
    var creator = new User("admin@example.com", "hash", "مدير");
    creator.setId(1L);
    var subscriber = new NewsletterSubscriber();
    subscriber.setEmail("reader@example.com");
    subscriber.setFrequency("weekly");
    subscriber.setTopics(Set.of("opportunities"));
    subscriber.setSubscribedAt(Instant.now());
    when(campaigns.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              NewsletterCampaign campaign = invocation.getArgument(0);
              campaign.setId(11L);
              return campaign;
            });
    when(subscribers.findByDeletedFalseOrderByIdAsc()).thenReturn(List.of(subscriber));

    var response =
        service.create(
            creator,
            new NewsletterCampaignDtos.CampaignRequest(
                "فرص الأسبوع", null, "<p>فرص جديدة</p>", "opportunities", null, true));

    assertThat(response.status()).isEqualTo(NewsletterCampaignStatus.SENT);
    assertThat(response.recipientCount()).isEqualTo(1);
    assertThat(response.sentCount()).isEqualTo(1);
    verify(delivery).send(any());
  }
}
