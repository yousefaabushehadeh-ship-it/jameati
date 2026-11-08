package com.jameati.payment.gateway.mpgs;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.subscription.*;
import com.jameati.user.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF"})
class MpgsFlowIntegrationTest {
  @Autowired AnnualMembershipRepository memberships;
  @Autowired UserRepository users;
  @Autowired MpgsPaymentAttemptStore attempts;
  @Autowired PlatformTransactionManager transactions;
  final ObjectMapper json = new ObjectMapper();

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void persistedFlowWithConcurrentReplaysActivatesOnlyAfterVerifiedPay(boolean challenge) throws Exception {
    var tx = new TransactionTemplate(transactions);
    var user = users.saveAndFlush(new User("mpgs-" + UUID.randomUUID() + "@example.test", "hash", "Payer"));
    var gateway = mock(MpgsClient.class);
    var plans = mock(SubscriptionPlanService.class, RETURNS_DEEP_STUBS);
    when(plans.current().enabled()).thenReturn(true);
    var service = new MpgsPaymentService(memberships, plans, users, gateway, attempts);
    when(gateway.createSession()).thenReturn(json.readTree("{\"result\":\"SUCCESS\",\"session\":{\"id\":\"SESSION-test-current-card-session\"}}"));
    when(gateway.hostedSessionScriptUrl()).thenReturn("https://ap.gateway.mastercard.com/session.js");
    when(gateway.returnUrl(anyLong())).thenReturn("https://example.test/subscription?mpgsCallback=1");
    var created = tx.execute(s -> service.create(user, new MpgsPaymentDtos.CreatePaymentRequest(MembershipPlanCode.ANNUAL)));
    String order = created.orderId(), auth = created.transactionId(), session = created.session().id();
    var sessionRequest = new MpgsPaymentDtos.SessionRequest(session);
    when(gateway.initiateAuthentication(eq(order), eq(auth), eq(session), anyString(), eq("JOD")))
        .thenReturn(json.readTree("""
          {"result":"SUCCESS","response":{"gatewayCode":"AUTHENTICATION_IN_PROGRESS","gatewayRecommendation":"PROCEED"},
           "transaction":{"authenticationStatus":"AUTHENTICATION_AVAILABLE"},"authentication":{"version":"3DS2"}}
          """));
    concurrent(() -> tx.execute(s -> service.initiate(user, created.paymentId(), sessionRequest)));
    var persisted = memberships.findById(created.paymentId()).orElseThrow();
    assertThat(persisted.getGatewayAuthenticationStatus()).isEqualTo("AUTHENTICATION_AVAILABLE");
    assertThat(persisted.getGatewayAuthenticationVersion()).isEqualTo("3DS2");
    assertThat(persisted.getGatewayAuthenticationTransactionId()).isEqualTo(auth);
    assertThat(service.pay(user, created.paymentId()).status()).isEqualTo(PaymentStatus.REQUIRES_AUTHENTICATION);
    verify(gateway, never()).retrieveTransaction(anyString(), anyString());
    var success = result(order, auth, "AUTHENTICATION_SUCCESSFUL", "AUTHENTICATION", "APPROVED");
    when(gateway.authenticatePayer(eq(order), eq(auth), eq(session), anyString(), eq("JOD"), anyString(), anyMap()))
        .thenReturn(challenge ? json.readTree("""
          {"result":"PENDING","response":{"gatewayCode":"AUTHENTICATION_IN_PROGRESS"},
           "transaction":{"authenticationStatus":"AUTHENTICATION_PENDING"},
           "authentication":{"redirectHtml":"<form>challenge</form>"}}
          """) : success);
    var request = new MpgsPaymentDtos.AuthenticateRequest(session,
        new MpgsPaymentDtos.BrowserDetails("browser", "text/html", "en", 1200, 800, 24, 0, false));
    concurrent(() -> tx.execute(s -> service.authenticate(user, created.paymentId(), request)));
    if (challenge) {
      when(gateway.retrieveTransaction(order, auth)).thenReturn(result(order, auth, "AUTHENTICATION_PENDING", "AUTHENTICATION", "AUTHENTICATION_IN_PROGRESS"));
      assertThat(service.pay(user, created.paymentId()).status()).isEqualTo(PaymentStatus.REQUIRES_AUTHENTICATION);
      assertThat(memberships.findById(created.paymentId()).orElseThrow().getStatus()).isEqualTo(MembershipStatus.PENDING);
    }
    when(gateway.retrieveTransaction(order, auth)).thenReturn(success);
    var payId = persisted.getGatewayTransactionId();
    var paid = result(order, payId, "AUTHENTICATION_SUCCESSFUL", "PAYMENT", "APPROVED");
    when(gateway.pay(eq(order), eq(payId), eq(auth), eq(session), anyString(), eq("JOD"))).thenReturn(paid);
    assertThat(service.pay(user, created.paymentId()).status()).isEqualTo(PaymentStatus.PAID);
    assertThat(service.pay(user, created.paymentId()).status()).isEqualTo(PaymentStatus.PAID);
    var activated = memberships.findById(created.paymentId()).orElseThrow();
    assertThat(activated.getStatus()).isEqualTo(MembershipStatus.PAID);
    assertThat(activated.getStartsAt()).isNotNull();
    assertThat(activated.getExpiresAt()).isAfter(activated.getStartsAt());
    verify(gateway, times(1)).initiateAuthentication(eq(order), eq(auth), eq(session), anyString(), eq("JOD"));
    verify(gateway, times(1)).authenticatePayer(eq(order), eq(auth), eq(session), anyString(), eq("JOD"), anyString(), anyMap());
    verify(gateway, times(1)).pay(eq(order), eq(payId), eq(auth), eq(session), anyString(), eq("JOD"));
  }
  private void concurrent(Callable<MpgsPaymentDtos.PaymentResponse> action) throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      Callable<MpgsPaymentDtos.PaymentResponse> task = () -> { gate.await(); return action.call(); };
      var one = executor.submit(task); var two = executor.submit(task); gate.countDown();
      assertThat(one.get(20, TimeUnit.SECONDS).status()).isEqualTo(two.get(20, TimeUnit.SECONDS).status());
    }
  }
  private JsonNode result(String order, String transaction, String status, String type, String code) throws Exception {
    return json.readTree("{\"result\":\"SUCCESS\",\"response\":{\"gatewayCode\":\"" + code
        + "\"},\"order\":{\"id\":\"" + order + "\",\"amount\":\"25.00\",\"currency\":\"JOD\"},"
        + "\"transaction\":{\"id\":\"" + transaction + "\",\"type\":\"" + type
        + "\",\"authenticationStatus\":\"" + status + "\"}}");
  }
}
