package com.jameati.email;

import static com.jameati.email.EmailDeliveryGateway.EmailMessage;
import static com.jameati.email.NewsletterCampaignDtos.*;

import com.jameati.support.NewsletterSubscriber;
import com.jameati.support.NewsletterSubscriberRepository;
import com.jameati.user.User;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NewsletterCampaignService {
  private final NewsletterCampaignRepository campaigns;
  private final NewsletterSubscriberRepository subscribers;
  private final EmailDeliveryGateway delivery;

  public NewsletterCampaignService(
      NewsletterCampaignRepository campaigns,
      NewsletterSubscriberRepository subscribers,
      EmailDeliveryGateway delivery) {
    this.campaigns = campaigns;
    this.subscribers = subscribers;
    this.delivery = delivery;
  }

  @Transactional(readOnly = true)
  public Page<CampaignResponse> history(Pageable pageable) {
    return campaigns.findByDeletedFalse(pageable).map(this::response);
  }

  public CampaignResponse create(User creator, CampaignRequest request) {
    var campaign = new NewsletterCampaign();
    campaign.setCreatedBy(creator);
    campaign.setSubject(request.subject().trim());
    campaign.setPreviewText(clean(request.previewText()));
    campaign.setHtmlContent(request.htmlContent().trim());
    campaign.setTopic(clean(request.topic()));
    campaign.setScheduledAt(request.scheduledAt());
    if (request.sendNow()) {
      campaign.setStatus(NewsletterCampaignStatus.DRAFT);
      campaigns.saveAndFlush(campaign);
      dispatch(campaign);
    } else if (request.scheduledAt() != null) {
      campaign.setStatus(NewsletterCampaignStatus.SCHEDULED);
      campaigns.save(campaign);
    } else {
      campaign.setStatus(NewsletterCampaignStatus.DRAFT);
      campaigns.save(campaign);
    }
    return response(campaign);
  }

  @Scheduled(fixedDelayString = "${app.mail.campaign-poll-ms:60000}")
  public void dispatchScheduled() {
    campaigns
        .findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            NewsletterCampaignStatus.SCHEDULED, Instant.now())
        .forEach(this::dispatch);
  }

  void dispatch(NewsletterCampaign campaign) {
    campaign.setStatus(NewsletterCampaignStatus.SENDING);
    campaign.setStartedAt(Instant.now());
    campaign.setCompletedAt(null);
    campaign.setErrorMessage(null);
    var recipients = recipients(campaign.getTopic());
    campaign.setRecipientCount(recipients.size());
    int sent = 0;
    int failed = 0;
    String firstError = null;
    for (var subscriber : recipients) {
      try {
        delivery.send(
            new EmailMessage(
                subscriber.getEmail(), campaign.getSubject(), campaign.getHtmlContent(), true));
        sent++;
      } catch (RuntimeException exception) {
        failed++;
        if (firstError == null) {
          firstError = safeMessage(exception);
        }
      }
    }
    campaign.setSentCount(sent);
    campaign.setFailedCount(failed);
    campaign.setCompletedAt(Instant.now());
    campaign.setErrorMessage(firstError);
    campaign.setStatus(
        failed == 0
            ? NewsletterCampaignStatus.SENT
            : sent == 0
                ? NewsletterCampaignStatus.FAILED
                : NewsletterCampaignStatus.COMPLETED_WITH_ERRORS);
  }

  private List<NewsletterSubscriber> recipients(String topic) {
    return subscribers.findByDeletedFalseOrderByIdAsc().stream()
        .filter(subscriber -> topic == null || subscriber.getTopics().contains(topic))
        .toList();
  }

  private CampaignResponse response(NewsletterCampaign campaign) {
    return new CampaignResponse(
        campaign.getId(),
        campaign.getSubject(),
        campaign.getPreviewText(),
        campaign.getTopic(),
        campaign.getStatus(),
        campaign.getScheduledAt(),
        campaign.getStartedAt(),
        campaign.getCompletedAt(),
        campaign.getRecipientCount(),
        campaign.getSentCount(),
        campaign.getFailedCount(),
        campaign.getErrorMessage(),
        campaign.getCreatedBy().getId(),
        campaign.getCreatedAt(),
        campaign.getUpdatedAt());
  }

  private static String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String safeMessage(RuntimeException exception) {
    var message = exception.getMessage();
    if (message == null || message.isBlank()) {
      message = exception.getClass().getSimpleName();
    }
    return message.length() > 1000 ? message.substring(0, 1000) : message;
  }
}
