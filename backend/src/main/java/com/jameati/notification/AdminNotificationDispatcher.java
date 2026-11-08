package com.jameati.notification;

import static com.jameati.email.EmailDeliveryGateway.EmailMessage;

import com.jameati.email.EmailDeliveryGateway;
import com.jameati.user.RoleName;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class AdminNotificationDispatcher {
  private static final int RECIPIENT_BATCH_SIZE = 250;
  private static final Duration STALE_JOB_AFTER = Duration.ofMinutes(15);
  private static final Set<RoleName> STAFF_ROLES =
      Set.of(RoleName.EDITOR, RoleName.DATA_ENTRY, RoleName.ADMIN, RoleName.SUPER_ADMIN);

  private final AdminNotificationDeliveryRepository deliveries;
  private final UserRepository users;
  private final AdminNotificationDeliveryStateService state;
  private final AdminNotificationRecipientService recipientDelivery;
  private final EmailDeliveryGateway emailDelivery;
  private final String frontendUrl;

  public AdminNotificationDispatcher(
      AdminNotificationDeliveryRepository deliveries,
      UserRepository users,
      AdminNotificationDeliveryStateService state,
      AdminNotificationRecipientService recipientDelivery,
      EmailDeliveryGateway emailDelivery,
      @Value("${app.mail.frontend-url:http://localhost:3000}") String frontendUrl) {
    this.deliveries = deliveries;
    this.users = users;
    this.state = state;
    this.recipientDelivery = recipientDelivery;
    this.emailDelivery = emailDelivery;
    this.frontendUrl = frontendUrl.replaceAll("/+$", "");
  }

  @Scheduled(fixedDelayString = "${app.notifications.poll-ms:30000}")
  public void dispatchDue() {
    var now = Instant.now();
    deliveries
        .findClaimableIds(
            NotificationDeliveryStatus.SCHEDULED,
            NotificationDeliveryStatus.SENDING,
            now,
            now.minus(STALE_JOB_AFTER),
            PageRequest.of(0, 20))
        .forEach(this::dispatchIfClaimed);
  }

  public boolean dispatchIfClaimed(Long id) {
    var now = Instant.now();
    if (!state.claim(id, now, now.minus(STALE_JOB_AFTER))) return false;

    String firstError = null;
    try {
      var plan = state.plan(id);
      var pageNumber = 0;
      boolean hasNext;
      var emailHtml = emailHtml(plan);
      do {
        var page =
            users.findByEnabledTrueAndDeletedFalseOrderByIdAsc(
                PageRequest.of(pageNumber, RECIPIENT_BATCH_SIZE));
        for (var user : page.getContent()) {
          if (!matches(plan.audience(), user)) continue;
          var recipientId =
              recipientDelivery.ensureRecipient(
                  plan.id(), user.getId(), plan.inAppEnabled(), plan.emailEnabled());
          if (plan.inAppEnabled()) {
            try {
              recipientDelivery.deliverInApp(
                  recipientId, plan.id(), plan.title(), plan.body(), plan.link());
            } catch (RuntimeException exception) {
              recipientDelivery.markInAppFailed(recipientId, message(exception));
              if (firstError == null) firstError = message(exception);
            }
          }
          if (plan.emailEnabled()) {
            try {
              if (recipientDelivery.beginEmail(recipientId, Instant.now())) {
                emailDelivery.send(new EmailMessage(user.getEmail(), plan.title(), emailHtml, true));
                recipientDelivery.markEmailSent(recipientId);
              }
            } catch (RuntimeException exception) {
              recipientDelivery.markEmailFailed(recipientId, message(exception));
              if (firstError == null) firstError = message(exception);
            }
          }
        }
        hasNext = page.hasNext();
        pageNumber++;
        state.heartbeat(id, Instant.now());
      } while (hasNext);
      state.complete(id, Instant.now(), firstError);
      return true;
    } catch (RuntimeException exception) {
      state.fail(id, Instant.now(), exception);
      return false;
    }
  }

  private static boolean matches(NotificationAudience audience, User user) {
    var staff = user.getRoles().stream().anyMatch(STAFF_ROLES::contains);
    return switch (audience) {
      case ALL -> true;
      case USERS -> !staff;
      case STAFF -> staff;
    };
  }

  private String emailHtml(AdminNotificationDeliveryStateService.DeliveryPlan plan) {
    var title = HtmlUtils.htmlEscape(plan.title());
    var body = HtmlUtils.htmlEscape(plan.body()).replace("\n", "<br>");
    var href =
        plan.link() != null && plan.link().startsWith("/")
            ? frontendUrl + plan.link()
            : plan.link();
    var link =
        href == null
            ? ""
            : "<p><a href=\""
                + HtmlUtils.htmlEscape(href)
                + "\">فتح التفاصيل في جامعتي</a></p>";
    return "<div dir=\"rtl\"><h2>" + title + "</h2><p>" + body + "</p>" + link + "</div>";
  }

  private static String message(RuntimeException exception) {
    var value = exception.getMessage();
    return value == null || value.isBlank() ? exception.getClass().getSimpleName() : value;
  }
}
