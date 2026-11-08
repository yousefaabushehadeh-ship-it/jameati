package com.jameati.payment.gateway.mpgs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.me.UserNotificationRepository;
import com.jameati.subscription.*;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MpgsPaymentAttemptStoreTest {
  private final ObjectMapper json = new ObjectMapper();
  private AnnualMembershipRepository memberships;
  private UserNotificationRepository notifications;
  private MpgsPaymentAttemptStore store;
  private User user;
  private AnnualMembership payment;

  @BeforeEach
  void setUp() {
    memberships = mock(AnnualMembershipRepository.class);
    notifications = mock(UserNotificationRepository.class);
    var users = mock(UserRepository.class);
    user = new User("payer@example.com", "hash", "Payer");
    user.setId(7L);
    payment = new AnnualMembership();
    payment.setId(91L);
    payment.setUser(user);
    payment.setPlanCode(MembershipPlanCode.ANNUAL);
    payment.setBillingPeriodMonths(12);
    payment.setAmount(new BigDecimal("35.00"));
    payment.setCurrency("JOD");
    payment.setStatus(MembershipStatus.PENDING);
    payment.setPaymentStatus(PaymentStatus.PROCESSING);
    payment.setPaymentProvider("MPGS");
    payment.setGatewayOrderId("JAM-order");
    payment.setGatewayAuthenticationTransactionId("AUTH-tx");
    payment.setGatewayTransactionId("PAY-tx");
    when(users.findEnabledByIdForUpdate(7L)).thenReturn(Optional.of(user));
    when(memberships.findByIdAndUserIdForUpdate(91L, 7L)).thenReturn(Optional.of(payment));
    store = new MpgsPaymentAttemptStore(memberships, users, notifications);
  }

  @Test void exactApprovedPaymentActivatesAtomically() throws Exception {
    var result = store.applyPaymentResult(user, 91L, paymentResult("JAM-order", "PAY-tx", "35.00", "JOD", "PAYMENT", "APPROVED"));
    assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    assertThat(result.getStatus()).isEqualTo(MembershipStatus.PAID);
    assertThat(result.getActivationSource()).isEqualTo("MPGS");
    verify(memberships).saveAndFlush(payment);
    verify(notifications).save(any());
  }

  @Test void mismatchedOrderNeverActivates() throws Exception {
    store.applyPaymentResult(user, 91L, paymentResult("OTHER", "PAY-tx", "35.00", "JOD", "PAYMENT", "APPROVED"));
    assertThat(payment.getStatus()).isEqualTo(MembershipStatus.PENDING);
    assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    verifyNoInteractions(notifications);
  }

  @Test void mismatchedAmountNeverActivates() throws Exception {
    store.applyPaymentResult(user, 91L, paymentResult("JAM-order", "PAY-tx", "34.00", "JOD", "PAYMENT", "APPROVED"));
    assertThat(payment.getStatus()).isEqualTo(MembershipStatus.PENDING);
    assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
  }

  @Test void wrongTransactionTypeNeverActivates() throws Exception {
    store.applyPaymentResult(user, 91L, paymentResult("JAM-order", "PAY-tx", "35.00", "JOD", "AUTHENTICATION", "APPROVED"));
    assertThat(payment.getStatus()).isEqualTo(MembershipStatus.PENDING);
  }

  @Test void authenticationRequiresExactStoredIdentifiersAndValue() throws Exception {
    var correct = json.readTree("{\"result\":\"SUCCESS\",\"order\":{\"id\":\"JAM-order\",\"amount\":\"35.00\",\"currency\":\"JOD\"},\"transaction\":{\"id\":\"AUTH-tx\",\"authenticationStatus\":\"AUTHENTICATION_SUCCESSFUL\"}}");
    var wrong = json.readTree("{\"result\":\"SUCCESS\",\"order\":{\"id\":\"JAM-order\",\"amount\":\"35.00\",\"currency\":\"JOD\"},\"transaction\":{\"id\":\"OTHER\",\"authenticationStatus\":\"AUTHENTICATION_SUCCESSFUL\"}}");
    assertThat(MpgsPaymentAttemptStore.validAuthentication(payment, correct)).isTrue();
    assertThat(MpgsPaymentAttemptStore.validAuthentication(payment, wrong)).isFalse();
  }

  @Test void anotherUserCannotInspectOrOperateThePayment() {
    var attacker = new User("attacker@example.com", "hash", "Attacker");
    attacker.setId(8L);
    var users = mock(UserRepository.class);
    when(users.findEnabledByIdForUpdate(8L)).thenReturn(Optional.of(attacker));
    var isolated = new MpgsPaymentAttemptStore(memberships, users, notifications);
    assertThatThrownBy(() -> isolated.inspect(attacker, 91L))
        .isInstanceOf(com.jameati.common.NotFoundException.class);
    verify(memberships).findByIdAndUserIdForUpdate(91L, 8L);
  }

  private com.fasterxml.jackson.databind.JsonNode paymentResult(String orderId, String transactionId,
      String amount, String currency, String type, String code) throws Exception {
    return json.readTree("{\"result\":\"SUCCESS\",\"response\":{\"gatewayCode\":\"" + code
        + "\"},\"order\":{\"id\":\"" + orderId + "\",\"amount\":\"" + amount
        + "\",\"currency\":\"" + currency + "\"},\"transaction\":{\"id\":\""
        + transactionId + "\",\"type\":\"" + type + "\"}}");
  }
}
