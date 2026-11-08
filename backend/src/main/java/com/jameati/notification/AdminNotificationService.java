package com.jameati.notification;

import static com.jameati.notification.AdminNotificationDtos.DeliveryRequest;
import static com.jameati.notification.AdminNotificationDtos.DeliveryResponse;

import com.jameati.user.User;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminNotificationService {
  private final AdminNotificationDeliveryRepository deliveries;
  private final AdminNotificationDeliveryStateService state;
  private final AdminNotificationDispatcher dispatcher;

  public AdminNotificationService(
      AdminNotificationDeliveryRepository deliveries,
      AdminNotificationDeliveryStateService state,
      AdminNotificationDispatcher dispatcher) {
    this.deliveries = deliveries;
    this.state = state;
    this.dispatcher = dispatcher;
  }

  @Transactional(readOnly = true)
  public Page<DeliveryResponse> history(Pageable pageable) {
    return deliveries
        .findByDeletedFalse(pageable)
        .map(AdminNotificationDeliveryStateService::toResponse);
  }

  public DeliveryResponse create(User creator, DeliveryRequest request) {
    var now = Instant.now();
    var id = state.create(creator, request, now);
    if (request.scheduledAt() == null) {
      dispatcher.dispatchIfClaimed(id);
    }
    return state.response(id);
  }
}
