package com.jameati.subscription;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.jameati.me.UserNotificationRepository;
import com.jameati.opportunity.Opportunity;
import com.jameati.opportunity.OpportunityRepository;
import com.jameati.user.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScholarshipSubscriptionAccessTest {
  @Test
  void checkoutRejectsOpportunityLockedByAnnualMembership() {
    var subscriptions = mock(ScholarshipSubscriptionRepository.class);
    var opportunities = mock(OpportunityRepository.class);
    var contentAccess = mock(ContentAccessService.class);
    var user = mock(User.class);
    var opportunity = new Opportunity();
    var context = mock(ContentAccessService.AccessContext.class);
    when(opportunities.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(opportunity));
    when(contentAccess.resolve(user)).thenReturn(context);
    when(contentAccess.isOpportunityLocked(context, 9L)).thenReturn(true);

    var service = new ScholarshipSubscriptionService(
        subscriptions,
        opportunities,
        mock(UserNotificationRepository.class),
        contentAccess);

    assertThatThrownBy(() -> service.checkout(user, 9L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("عضوية سنوية");
    verifyNoInteractions(subscriptions);
  }
}
